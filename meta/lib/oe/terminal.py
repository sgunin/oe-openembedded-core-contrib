import logging
import os
import oe.classutils
import oe.data
import shlex
import bb
from bb.process import Popen, ExecutionError


logger = logging.getLogger('BitBake.OE.Terminal')


class UnsupportedTerminal(StandardError):
    pass

class NoSupportedTerminals(StandardError):
    pass


class Registry(oe.classutils.ClassRegistry):
    command = None

    def __init__(cls, name, bases, attrs):
        super(Registry, cls).__init__(name.lower(), bases, attrs)

    @property
    def implemented(cls):
        return bool(cls.command)


class Terminal(Popen):
    __metaclass__ = Registry

    def __init__(self, command, title=None):
        self.format_command(command, title)
        logger.debug(1, "%s: running %s", self.name, self.command)

        try:
            Popen.__init__(self, self.command, shell=False)
        except OSError as exc:
            import errno
            if exc.errno == errno.ENOENT:
                raise UnsupportedTerminal(self.name)
            else:
                raise

    def format_command(self, command, title):
        fmt = {'title': title or 'Terminal', 'command': command}
        if isinstance(self.command, basestring):
            self.command = shlex.split(self.command.format(**fmt))
        else:
            self.command = [element.format(**fmt) for element in self.command]

class XTerminal(Terminal):
    def __init__(self, command, title=None):
        Terminal.__init__(self, command, title)
        if not os.environ.get('DISPLAY'):
            raise UnsupportedTerminal(self.name)

class Gnome(XTerminal):
    command = 'gnome-terminal --disable-factory -t "{title}" -x {command}'
    priority = 2

class Konsole(XTerminal):
    command = 'konsole -T "{title}" -e {command}'
    priority = 2

class XTerm(XTerminal):
    command = 'xterm -T "{title}" -e {command}'
    priority = 1

class Rxvt(XTerminal):
    command = 'rxvt -T "{title}" -e {command}'
    priority = 1

class Screen(Terminal):
    command = 'screen -D -m -t "{title}" -S devshell {command}'

    def __init__(self, command, title=None):
        Terminal.__init__(self, command, title)
        logger.warn('Screen started. Please connect in another terminal with '
                    '"screen -r devshell"')


def prioritized():
    return Registry.prioritized()

def run(command, title, d):
    terminal = oe.data.typed_value('OE_TERMINAL', d).lower()
    if terminal == 'none':
        bb.fatal('Devshell usage disabled with OE_TERMINAL')
    elif terminal != 'auto':
        try:
            spawn(terminal, command, title)
            return
        except UnsupportedTerminal:
            bb.warn('Unsupported terminal "%s", defaulting to "auto"' %
                    terminal)
        except ExecutionError as exc:
            bb.fatal('Unable to spawn terminal %s: %s' % (terminal, exc))

    try:
        spawn_preferred(command, title)
    except NoSupportedTerminals:
        bb.fatal('No valid terminal found, unable to open devshell')
    except ExecutionError as exc:
        bb.fatal('Unable to spawn terminal %s: %s' % (terminal, exc))

def spawn_preferred(command, title=None):
    """Spawn the first supported terminal, by priority"""
    for terminal in prioritized():
        try:
            spawn(terminal.name, command, title)
            break
        except UnsupportedTerminal:
            continue
    else:
        raise NoSupportedTerminals()

def spawn(name, command, title=None):
    """Spawn the specified terminal, by name"""
    logger.debug(1, 'Attempting to spawn terminal "%s"', name)
    try:
        terminal = Registry.registry[name]
    except KeyError:
        raise UnsupportedTerminal(name)

    pipe = terminal(command, title)
    output = pipe.communicate()[0]
    if pipe.returncode != 0:
        raise ExecutionError(pipe.command, pipe.returncode, output)
