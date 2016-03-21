"""Helper module for GPG signing"""
import os
import time

import bb
import oe.utils


class Pexpect(object):
    """Naive and limited (p)expect functionality"""
    class PexpectErr(Exception):
        """Pexpect error"""
        pass

    def __init__(self, cmd):
        import pty
        self.pid, self.fd = pty.fork()
        if self.pid == 0:
            os.execv(cmd[0], cmd)
        self.status = None
        self.buf = ''
        self.buf_readp = 0

    def check_exitstatus(self):
        """Return child status or None if still alive"""
        if self.status is None:
            pid, status = os.waitpid(self.pid, os.WNOHANG)
            if pid != 0:
                self.status = status
        return self.status

    def close(self):
        """Close connection and terminate our child"""
        import signal
        if self.fd == -1:
            return
        os.close(self.fd)
        self.fd = -1
        time.sleep(0.1)

        # Kill child process if it's still alive
        if self.check_exitstatus() is None:
            os.kill(self.pid, signal.SIGHUP)
            # Give the process some time to terminate peacefully
            time.sleep(0.5)
            if self.check_exitstatus() is None:
                os.kill(self.pid, signal.SIGKILL)
                time.sleep(0.5)
                if self.check_exitstatus() is None:
                    bb.warn('Failed to kill PID %d' % self.pid)

    def expect_exact(self, expected, timeout):
        """Wait until expected output is detected. Use None to wait until EOF"""
        import errno
        import select
        end_time = time.time() + timeout
        while time.time() < end_time:
            ready = select.select([self.fd], [], [], end_time - time.time())
            if ready[0]:
                try:
                    self.buf += os.read(self.fd, 4096)
                except OSError as err:
                    if err.errno == errno.EIO:
                        if expected is None:
                            return
                        else:
                            raise self.PexpectErr("Unexpected EOF")
                    raise
                if expected is not None:
                    ind = self.buf.find(expected, self.buf_readp)
                    if ind >= 0:
                        self.buf_readp = ind + len(expected)
                        return
                    elif len(self.buf) > len(expected):
                        # No need to search from beginning of buf every time
                        self.buf_readp = len(self.buf) - len(expected)
        raise self.PexpectErr("Timeout")

    def sendline(self, data):
        """Write data to child proces stdin"""
        os.write(self.fd, data)
        os.write(self.fd, '\n')


class LocalSigner(object):
    """Class for handling local (on the build host) signing"""
    def __init__(self, d):
        self.gpg_bin = d.getVar('GPG_BIN', True) or \
                  bb.utils.which(os.getenv('PATH'), 'gpg')
        self.gpg_path = d.getVar('GPG_PATH', True)
        self.rpm_bin = bb.utils.which(os.getenv('PATH'), "rpm")

    def export_pubkey(self, output_file, keyid, armor=True):
        """Export GPG public key to a file"""
        cmd = '%s --batch --yes --export -o %s ' % \
                (self.gpg_bin, output_file)
        if self.gpg_path:
            cmd += "--homedir %s " % self.gpg_path
        if armor:
            cmd += "--armor "
        cmd += keyid
        status, output = oe.utils.getstatusoutput(cmd)
        if status:
            raise bb.build.FuncFailed('Failed to export gpg public key (%s): %s' %
                                      (keyid, output))

    def sign_rpms(self, files, keyid, passphrase_file):
        """Sign RPM files"""
        cmd = [self.rpm_bin, '--addsign', '--define', '_gpg_name ' + keyid]
        if self.gpg_bin:
            cmd += ['--define', '__gpg ' + self.gpg_bin]
        if self.gpg_path:
            cmd += ['--define', '_gpg_path ' + self.gpg_path]
        cmd += files

        # Need to use pexpect for feeding the passphrase
        proc = Pexpect(cmd)
        try:
            proc.expect_exact('Enter pass phrase:', timeout=15)
            with open(passphrase_file) as fobj:
                proc.sendline(fobj.readline().rstrip('\n'))
            proc.expect_exact(None, timeout=900)
        except Pexpect.PexpectErr as err:
            bb.error('rpmsign unexpected output: %s' % err)
        proc.close()
        status = proc.check_exitstatus()
        if os.WEXITSTATUS(status) or not os.WIFEXITED(status):
            bb.error('rpmsign failed: %s' % proc.buf[proc.buf_readp:])
            raise bb.build.FuncFailed("Failed to sign RPM packages")


    def detach_sign(self, input_file, keyid, passphrase_file, passphrase=None, armor=True):
        """Create a detached signature of a file"""
        import subprocess

        if passphrase_file and passphrase:
            raise Exception("You should use either passphrase_file of passphrase, not both")

        cmd = [self.gpg_bin, '--detach-sign', '--batch', '--no-tty', '--yes',
               '--passphrase-fd', '0', '-u', keyid]

        if self.gpg_path:
            cmd += ['--homedir', self.gpg_path]
        if armor:
            cmd += ['--armor']

        #gpg > 2.1 supports password pipes only through the loopback interface
        #gpg < 2.1 errors out if given unknown parameters
        dots = self.get_gpg_version().split('.')
        assert len(dots) >= 2
        if int(dots[0]) >= 2 and int(dots[1]) >= 1:
            cmd += ['--pinentry-mode', 'loopback']

        cmd += [input_file]

        try:
            if passphrase_file:
                with open(passphrase_file) as fobj:
                    passphrase = fobj.readline();

            job = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)
            (_, stderr) = job.communicate(passphrase)

            if job.returncode:
                raise bb.build.FuncFailed("GPG exited with code %d: %s" %
                                          (job.returncode, stderr))

        except IOError as e:
            bb.error("IO error (%s): %s" % (e.errno, e.strerror))
            raise Exception("Failed to sign '%s'" % input_file)

        except OSError as e:
            bb.error("OS error (%s): %s" % (e.errno, e.strerror))
            raise Exception("Failed to sign '%s" % input_file)


    def get_gpg_version(self):
        """Return the gpg version"""
        import subprocess
        try:
            return subprocess.check_output((self.gpg_bin, "--version")).split()[2]
        except subprocess.CalledProcessError as e:
            raise bb.build.FuncFailed("Could not get gpg version: %s" % e)


    def verify(self, sig_file):
        """Verify signature"""
        cmd = self.gpg_bin + " --verify "
        if self.gpg_path:
            cmd += "--homedir %s " % self.gpg_path
        cmd += sig_file
        status, _ = oe.utils.getstatusoutput(cmd)
        ret = False if status else True
        return ret


def get_signer(d, backend):
    """Get signer object for the specified backend"""
    # Use local signing by default
    if backend == 'local':
        return LocalSigner(d)
    else:
        bb.fatal("Unsupported signing backend '%s'" % backend)

