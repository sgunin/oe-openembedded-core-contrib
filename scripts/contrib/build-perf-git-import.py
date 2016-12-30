#!/usr/bin/python3
#
# Copyright (c) 2016, Intel Corporation.
#
# This program is free software; you can redistribute it and/or modify it
# under the terms and conditions of the GNU General Public License,
# version 2, as published by the Free Software Foundation.
#
# This program is distributed in the hope it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
# more details.
#
import argparse
import csv
import json
import locale
import logging
import os
import re
import shutil
import sys
import tempfile
import time
from collections import defaultdict, OrderedDict
from datetime import datetime, timedelta, tzinfo
from glob import glob
from subprocess import check_output, CalledProcessError

# Import oe libs
scripts_path = os.path.dirname(os.path.realpath(__file__))
sys.path.append(os.path.join(scripts_path, '../lib'))
import scriptpath
scriptpath.add_oe_lib_path()

from oeqa.utils.git import GitRepo, GitError


# Setup logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s',
                    stream=sys.stdout)
log = logging.getLogger()


class CommitError(Exception):
    """Script's internal error handling"""
    pass

class ConversionError(Exception):
    """Error in converting results"""
    pass


class ResultsJsonEncoder(json.JSONEncoder):
    """Extended encoder for build perf test results"""
    unix_epoch = datetime.utcfromtimestamp(0)

    def default(self, obj):
        """Encoder for our types"""
        if isinstance(obj, datetime):
            # NOTE: we assume that all timestamps are in UTC time
            return (obj - self.unix_epoch).total_seconds()
        if isinstance(obj, timedelta):
            return obj.total_seconds()
        return json.JSONEncoder.default(self, obj)


class TimeZone(tzinfo):
    """Simple fixed-offset tzinfo"""
    def __init__(self, seconds, name):
        self._offset = timedelta(seconds=seconds)
        self._name = name

    def utcoffset(self, dt):
        return self._offset

    def tzname(self, dt):
        return self._name

    def dst(self, dt):
        return None

TIMEZONES = {'UTC': TimeZone(0, 'UTC'),
             'EDT': TimeZone(-18000, 'EDT'),
             'EST': TimeZone(-14400, 'EST'),
             'ET': TimeZone(-14400, 'ET'),
             'EET': TimeZone(7200, 'EET'),
             'EEST': TimeZone(10800, 'EEST')}


class OutputLogRecord(object):
    """Class representing one row in the log"""
    def __init__(self, timestamp, msg):
        self.time = timestamp
        self.msg = msg

    def __str__(self):
        return "[{}] {}".format(self.time.isoformat(), self.msg)

class OutputLog(object):
    """Class representing the 'old style' main output log"""
    def __init__(self, filepath):
        self.new_fmt = False
        self.records = []
        self._start = None
        self._head = None
        self._end = None
        self._read(filepath)

    @staticmethod
    def _parse_line_old_default(line):
        """Parse "old" style line in C locale"""
        split = line.split(None, 6)
        try:
            timestamp = datetime.strptime(' '.join(split[0:4] + split[5:6]),
                                          '%a %b %d %H:%M:%S %Y:')
        except ValueError:
            raise ConversionError("Unable to parse RO timestamp")
        timezone = TIMEZONES[split[4]]
        return timestamp, timezone, split[6].strip()

    @staticmethod
    def _parse_line_old_ro(line):
        """Parse "old" style line in RO locale"""
        split = line.split(None, 6)
        try:
            timestamp = datetime.strptime(' '.join(split[0:5]), '%A %d %B %Y, %H:%M:%S')
        except ValueError:
            raise ConversionError("Unable to parse RO timestamp")
        hhmm = split[5]
        offset = int(hhmm[0] + '1') * (int(hhmm[1:3])*3600 + int(hhmm[3:5])*60)
        timezone = TimeZone(offset, hhmm)
        return timestamp, timezone, split[6].strip()

    def _read(self, filepath):
        """Read 'old style' output.log"""

        orig_locale = locale.getlocale()
        fobj = open(filepath)
        try:
            # Check if the log is from the old shell-based or new Python script
            if fobj.read(1) == '[':
                self.new_fmt = True
            else:
                # Determine timestamp format
                fobj.seek(0)
                line = fobj.readline()
                try:
                    locale.setlocale(locale.LC_ALL, 'C')
                    self._parse_line_old_default(line)
                    parse_line = self._parse_line_old_default
                except ConversionError:
                    parse_line = None
                if not parse_line:
                    try:
                        locale.setlocale(locale.LC_ALL, 'ro_RO.UTF-8')
                        self._parse_line_old_ro(line)
                        parse_line = self._parse_line_old_ro
                    except ConversionError:
                        raise ConversionError("Unable to parse output.log timestamps")
            fobj.seek(0)

            for line in fobj.readlines():
                if self.new_fmt:
                    split = line.split(']', 1)
                    try:
                        timestamp = datetime.strptime(split[0],
                                                      '[%Y-%m-%d %H:%M:%S,%f')
                    except ValueError:
                        # Seems to be multi-line record, append to last msg
                        self.records[-1].msg += '\n' + line.rstrip()
                    else:
                        self.records.append(OutputLogRecord(timestamp,
                                                            split[1].strip()))
                else:
                    timestamp, timezone, message = parse_line(line)
                    # Convert timestamps to UTC time
                    timestamp = timestamp - timezone.utcoffset(timestamp)
                    #timestamp = timestamp.replace(tzinfo=TIMEZONES['UTC'])
                    timestamp = timestamp.replace(tzinfo=None)
                    self.records.append(OutputLogRecord(timestamp, message))
        finally:
            fobj.close()
            locale.setlocale(locale.LC_ALL, orig_locale)

    def _find(self, regex, start=0, end=None):
        """Find record matching regex"""
        if end is None:
            end = len(self.records)
        re_c = re.compile(regex)
        for i in range(start, end):
            if re_c.match(self.records[i].msg):
                return i
        raise ConversionError("No match for regex '{}' in output.log between "
                              "lines {} and {}".format(regex, start+1, end+1))

    def set_start(self, regex):
        """Set test start point in log"""
        i = self._find(regex)
        self._start = self._head = i
        self._end = None
        return self.records[i]

    def set_end(self, regex):
        """Set test start point in log"""
        i = self._find(regex, start=self._start)
        self._end = i
        return self.records[i]

    def find(self, regex):
        """Find record matching regex between head and end"""
        i = self._find(regex, self._head, self._end)
        self._head = i + 1
        return self.records[i]

    def get_git_rev_info(self):
        """Helper for getting target branch name"""
        if self.new_fmt:
            rev_re = r'INFO: Using Git branch:revision (\S+):(\S+)'
        else:
            rev_re = r'Running on (\S+):(\S+)'
        branch, rev = re.match(rev_re, self.records[0].msg).groups()
        # Map all detached checkouts to '(nobranch)'
        if branch.startswith('(detachedfrom') or branch.startswith('(HEADdetachedat'):
            branch = '(nobranch)'
        return branch, rev

    def get_test_descr(self):
        """Helper for getting test description from 'start' row"""
        return self.records[self._start].msg.split(':', 1)[1].strip()

    def get_sysres_meas_start_time(self):
        """Helper for getting 'legend' for next sysres measurement"""
        record = self.find("Timing: ")
        return record.time

    def get_sysres_meas_time(self):
        """Helper for getting wall clock time of sysres measurement"""
        msg = self.find("TIME: ").msg
        return msg.split(':', 1)[1].strip()

    def get_du_meas_size(self):
        """Helper for getting size of du measurement"""
        msg = self.find(".*SIZE of.*: ").msg
        value = msg.split(':', 1)[1].strip()
        # Split out possible unit
        return int(value.split()[0])


def time_log_to_json(time_log):
    """Convert time log to json results"""
    def str_time_to_timedelta(strtime):
        """Convert time strig from the time utility to timedelta"""
        split = strtime.split(':')
        hours = int(split[0]) if len(split) > 2 else 0
        mins = int(split[-2]) if len(split) > 1 else 0

        split = split[-1].split('.')
        secs = int(split[0])
        frac = split[1] if len(split) > 1 else '0'
        microsecs = int(float('0.' + frac) * pow(10, 6))

        return timedelta(0, hours*3600 + mins*60 + secs, microsecs)

    res = {'rusage': {}}
    log.debug("Parsing time log: %s", time_log)
    exit_status = None
    with open(time_log) as fobj:
        for line in fobj.readlines():
            key, val = line.strip().rsplit(' ', 1)
            val = val.strip()
            key = key.rstrip(':')
            # Parse all fields
            if key == 'Exit status':
                exit_status = int(val)
            elif key.startswith('Elapsed (wall clock)'):
                res['elapsed_time'] = str_time_to_timedelta(val)
            elif key == 'User time (seconds)':
                res['rusage']['ru_utime'] = str_time_to_timedelta(val)
            elif key == 'System time (seconds)':
                res['rusage']['ru_stime'] = str_time_to_timedelta(val)
            elif key == 'Maximum resident set size (kbytes)':
                res['rusage']['ru_maxrss'] = int(val)
            elif key == 'Major (requiring I/O) page faults':
                res['rusage']['ru_majflt'] = int(val)
            elif key == 'Minor (reclaiming a frame) page faults':
                res['rusage']['ru_minflt'] = int(val)
            elif key == 'Voluntary context switches':
                res['rusage']['ru_nvcsw'] = int(val)
            elif key == 'Involuntary context switches':
                res['rusage']['ru_nivcsw'] = int(val)
            elif key == 'File system inputs':
                res['rusage']['ru_inblock'] = int(val)
            elif key == 'File system outputs':
                res['rusage']['ru_oublock'] = int(val)
    if exit_status is None:
        raise ConversionError("Truncated log file '{}'".format(
            os.path.basename(time_log)))
    return exit_status, res


def convert_buildstats(indir, outfile):
    """Convert buildstats into JSON format"""
    def split_nevr(nevr):
        """Split name and version information from recipe "nevr" string"""
        n_e_v, revision = nevr.rsplit('-', 1)
        match = re.match(r'^(?P<name>\S+)-((?P<epoch>[0-9]{1,5})_)?(?P<version>[0-9]\S*)$',
                         n_e_v)
        if not match:
            # If we're not able to parse a version starting with a number, just
            # take the part after last dash
            match = re.match(r'^(?P<name>\S+)-((?P<epoch>[0-9]{1,5})_)?(?P<version>[^-]+)$',
                             n_e_v)
        name = match.group('name')
        version = match.group('version')
        epoch = match.group('epoch')
        return name, epoch, version, revision

    def bs_to_json(filename):
        """Convert (task) buildstats file into json format"""
        bs_json = {'iostat': {},
                   'rusage': {},
                   'child_rusage': {}}
        end_time = None
        with open(filename) as fobj:
            for line in fobj.readlines():
                key, val = line.split(':', 1)
                val = val.strip()
                if key == 'Started':
                    start_time = datetime.utcfromtimestamp(float(val))
                    bs_json['start_time'] = start_time
                elif key == 'Ended':
                    end_time = datetime.utcfromtimestamp(float(val))
                elif key.startswith('IO '):
                    split = key.split()
                    bs_json['iostat'][split[1]] = int(val)
                elif key.find('rusage') >= 0:
                    split = key.split()
                    ru_key = split[-1]
                    if ru_key in ('ru_stime', 'ru_utime'):
                        val = float(val)
                    else:
                        val = int(val)
                    ru_type = 'rusage' if split[0] == 'rusage' else \
                                                      'child_rusage'
                    bs_json[ru_type][ru_key] = val
                elif key == 'Status':
                    bs_json['status'] = val
        if end_time is None:
            return None
        bs_json['elapsed_time'] = end_time - start_time
        return bs_json

    log.debug('Converting buildstats %s -> %s', indir, outfile)
    buildstats = []
    for fname in os.listdir(indir):
        recipe_dir = os.path.join(indir, fname)
        if not os.path.isdir(recipe_dir):
            continue
        name, epoch, version, revision = split_nevr(fname)
        recipe_bs = {'name': name,
                     'epoch': epoch,
                     'version': version,
                     'revision': revision,
                     'tasks': {}}
        for task in os.listdir(recipe_dir):
            task_bs = bs_to_json(os.path.join(recipe_dir, task))
            if not task_bs:
                raise ConversionError("Incomplete buildstats in {}:{}".format(
                    fname, task))
            recipe_bs['tasks'][task] = task_bs
        buildstats.append(recipe_bs)

    # Write buildstats into json file
    with open(outfile, 'w') as fobj:
        json.dump(buildstats, fobj, indent=4, sort_keys=True,
                  cls=ResultsJsonEncoder)


def convert_results(poky_repo, results_dir, tester_host):
    """Convert 'old style' to new JSON based format.

    Conversion is a destructive operation, converted files being deleted.
    """
    test_descriptions = {'test1': "Build core-image-sato",
                         'test12': "Build virtual/kernel",
                         'test13': "Build core-image-sato with rm_work enabled",
                         'test2': "Run core-image-sato do_rootfs with sstate",
                         'test3': "Bitbake parsing (bitbake -p)",
                         'test4': "eSDK metrics"}
    test_params = OrderedDict([
        ('test1', {'log_start_re': "Running Test 1, part 1/3",
                   'log_end_re': "Buildstats are saved in.*-test1$",
                   'meas_params': [('sysres', (1, 'build', 'bitbake core-image-sato')),
                                   ('diskusage', ('tmpdir', 'tmpdir'))],
                   }),
        ('test12', {'log_start_re': "Running Test 1, part 2/3",
                   'log_end_re': "More stats can be found in.*results.log.2",
                   'meas_params': [('sysres', (2, 'build', 'bitbake virtual/kernel'))],
                   }),
        ('test13', {'log_start_re': "Running Test 1, part 3/3",
                    'log_end_re': "Buildstats are saved in.*-test13$",
                    'meas_params': [('sysres', (3, 'build', 'bitbake core-image-sato')),
                                    ('diskusage', ('tmpdir', 'tmpdir'))],
                    }),
        ('test2', {'log_start_re': "Running Test 2",
                   'log_end_re': "More stats can be found in.*results.log.4",
                   'meas_params': [('sysres', (4, 'do_rootfs', 'bitbake do_rootfs'))],
                   }),
        ('test3', {'log_start_re': "Running Test 3",
                   'log_end_re': "More stats can be found in.*results.log.7",
                   'meas_params': [('sysres', (5, 'parse_1', 'bitbake -p (no caches)')),
                                   ('sysres', (6, 'parse_2', 'bitbake -p (no tmp/cache)')),
                                   ('sysres', (7, 'parse_3', 'bitbake -p (cached)'))],
                   }),
        ('test4', {'log_start_re': "Running Test 4",
                   'log_end_re': "All done, cleaning up",
                   'meas_params': [('diskusage', ('installer_bin', 'eSDK installer')),
                                   ('sysres', (8, 'deploy', 'eSDK deploy')),
                                   ('diskusage', ('deploy_dir', 'deploy dir'))],
                   })
         ])

    def _import_test(topdir, name, output_log, log_start_re, log_end_re,
                     meas_params):
        """Import test results from one results.log.X into JSON format"""
        test_res = {'name': name,
                    'measurements': [],
                    'status': 'SUCCESS'}
        start_time = output_log.set_start(log_start_re).time
        end_time = output_log.set_end(log_end_re).time
        test_res['description'] = test_descriptions[name]
        test_res['start_time'] = start_time
        test_res['elapsed_time'] = end_time - start_time
        for meas_type, params in meas_params:
            measurement = {'type': meas_type}
            if meas_type == 'sysres':
                i, meas_name, meas_legend = params
                start_time = output_log.get_sysres_meas_start_time()

                time_log_fn = os.path.join(topdir, 'results.log.{}'.format(i))
                if not os.path.isfile(time_log_fn):
                    raise ConversionError("results.log.{} not found".format(i))
                exit_status, measurement['values'] = time_log_to_json(time_log_fn)
                # Remove old results.log
                os.unlink(time_log_fn)

                if exit_status != 0:
                    log.debug("Detected failed test %s in %s", name, topdir)
                    test_res['status'] = 'ERROR'
                    # Consider the rest of the measurements (including this)
                    # invalid. Return what we got so far
                    return test_res

                measurement['values']['start_time'] = start_time
            elif meas_type == 'diskusage':
                meas_name, meas_legend = params
                try:
                    measurement['values'] = {'size': output_log.get_du_meas_size()}
                except ConversionError:
                    # Test4 might not have the second du measurement
                    if meas_name == 'deploy_dir':
                        log.debug("deploy_dir measurement for test4 not found")
                        continue
                    else:
                        raise
            else:
                raise CommitError("BUG: invalid measurement type: {}".format(meas_type))

            measurement['name'] = meas_name
            measurement['legend'] = meas_legend
            test_res['measurements'].append(measurement)
        return test_res


    # Read main logfile
    out_log = OutputLog(os.path.join(results_dir, 'output.log'))
    if out_log.new_fmt:
        raise ConversionError("New output.log format detected, refusing to "
                              "convert results")
    git_branch, git_rev = out_log.get_git_rev_info()

    tests = OrderedDict()

    # Parse test results
    for test, params in test_params.items():
        # Special handling for test4
        if (test == 'test4' and
                not os.path.exists(os.path.join(results_dir, 'results.log.8'))):
            continue
        try:
            tests[test] = _import_test(results_dir, test, out_log, **params)
        except ConversionError as err:
            raise ConversionError("Presumably incomplete test run. Unable to "
                                  "parse '{}' from output.log: {}".format(test, err))

    # Convert buildstats
    for path in glob(results_dir + '/buildstats-*'):
        testname = os.path.basename(path).split('-', 1)[1]
        if not testname in ('test1', 'test13'):
            raise CommitError("Unkown buildstats: {}".format(
                os.path.basename(path)))

        # No measurements indicates failed test -> don't import buildstats
        if tests[testname]['measurements']:
            bs_relpath = os.path.join(testname, 'buildstats.json')
            os.mkdir(os.path.join(results_dir, testname))
            try:
                convert_buildstats(path, os.path.join(results_dir, bs_relpath))
            except ConversionError as err:
                log.warn("Buildstats for %s not imported: %s", testname, err)
            else:
                # We know that buildstats have only been saved for the first
                # measurement of the two tests.
                tests[testname]['measurements'][0]['values']['buildstats_file'] = \
                    bs_relpath
        # Remove old buildstats directory
        shutil.rmtree(path)

    # Create final results dict
    cmd = ['rev-list', '--count', git_rev, '--']
    commit_cnt = poky_repo.run_cmd(cmd).splitlines()[0]
    results = OrderedDict((('tester_host', tester_host),
                           ('start_time', out_log.records[0].time),
                           ('elapsed_time', (out_log.records[-1].time -
                                             out_log.records[0].time)),
                           ('git_branch', git_branch),
                           ('git_commit', git_rev),
                           ('git_commit_count', commit_cnt),
                           ('product', 'poky'),
                           ('tests', tests)))

    # Write results.json
    with open(os.path.join(results_dir, 'results.json'), 'w') as fobj:
        json.dump(results, fobj, indent=4, cls=ResultsJsonEncoder)


def git_commit_dir(data_repo, src_dir, branch, msg, tag=None, tag_msg="",
                   timestamp=None):
    """Commit the content of a directory to a branch"""
    env = {'GIT_WORK_TREE': os.path.abspath(src_dir)}
    if timestamp:
        env['GIT_COMMITTER_DATE'] = timestamp
        env['GIT_AUTHOR_DATE'] = timestamp

    log.debug('Committing %s to git branch %s', src_dir, branch)
    data_repo.run_cmd(['symbolic-ref', 'HEAD', 'refs/heads/' + branch], env)
    data_repo.run_cmd(['add', '.'], env)
    data_repo.run_cmd(['commit', '-m', msg], env)

    log.debug('Tagging %s', tag)
    data_repo.run_cmd(['tag', '-a', '-m', tag_msg, tag, 'HEAD'], env)


def import_testrun(archive, data_repo, poky_repo, branch_fmt, tag_fmt,
                   convert=False):
    """Import one testrun into Git"""
    archive = os.path.abspath(archive)
    archive_fn = os.path.basename(archive)

    fields = archive_fn.split('-')
    fn_fields = {'timestamp': fields[-1].split('.')[0],
                 'rev': fields[-2],
                 'host': None}
    if os.path.isfile(archive):
        if len(fields) != 4:
            log.warn('Invalid archive %s, skipping...', archive)
            return False, "Invalid filename"
        fn_fields['host'] = fields[0]
    elif os.path.isdir(archive):
        fn_fields['host'] =  os.environ.get('BUILD_PERF_GIT_IMPORT_HOST')
        if not fn_fields['host'] and not convert:
            raise CommitError("You need to define tester host in "
                              "BUILD_PERF_GIT_IMPORT_HOST env var "
                              "when raw importing directories")
    else:
        raise CommitError("{} does not exist".format(archive))

    # Check that the commit is valid
    if poky_repo.rev_parse(fn_fields['rev']) is None:
        log.warn("Commit %s not found in Poky Git, skipping...", fn_fields['rev'])
        return False, "Commit {} not found in Poky Git".format(fn_fields['rev'])

    tmpdir = os.path.abspath(tempfile.mkdtemp(dir='.'))
    try:
        # Unpack tarball
        if os.path.isfile(archive):
            log.info('Unpacking %s', archive)
            # Unpack in two stages in order to skip (possible) build data
            check_output(['tar', '-xf', archive, '-C', tmpdir,
                          '--exclude', 'build/*'])
            try:
                check_output(['tar', '-xf', archive, '-C', tmpdir,
                              '--wildcards', '*/build/conf'])
            except CalledProcessError:
                log.warn("Archive doesn't contain build/conf")
            if len(os.listdir(tmpdir)) > 1:
                log.warn("%s contains multiple subdirs!", archive)
            results_dir = '{}-{}-{}'.format('results', fn_fields['rev'],
                                            fn_fields['timestamp'])
            results_dir = os.path.join(tmpdir, results_dir)
            if not os.path.exists(results_dir):
                log.warn("%s does not contain '%s/', skipping...",
                         archive, os.path.basename(results_dir))
                return False, "Invalid content"
        else:
            # Make a safe copy, filtering out possible build data
            results_dir = os.path.join(tmpdir, archive_fn)
            log.debug('Copying %s', archive)
            os.mkdir(results_dir)
            for f in glob(archive + '/*'):
                tgt_path = os.path.join(results_dir, os.path.basename(f))
                if os.path.isfile(f):
                    # Regular files
                    shutil.copy2(f, tgt_path)
                elif os.path.basename(f) == 'build':
                    # From build dir we only want to conf
                    os.mkdir(tgt_path)
                    shutil.copytree(os.path.join(f, 'conf'),
                                    os.path.join(tgt_path, 'conf'))
                else:
                    # Other directories are copied as is
                    shutil.copytree(f, tgt_path)

        # Remove redundant buildstats subdir(s)
        for buildstat_dir in glob(results_dir + '/buildstats-*'):
            buildstat_tmpdir = buildstat_dir + '.tmp'
            shutil.move(buildstat_dir, buildstat_tmpdir)
            builds = sorted(glob(buildstat_tmpdir + '/*'))
            buildstat_subdir = builds[-1]
            if len(builds) != 1:
                log.warn('%s in %s contains multiple builds, using only %s',
                         os.path.basename(buildstat_dir), archive,
                         os.path.basename(buildstat_subdir))

            # Handle the formerly used two-level buildstat directory structure
            # (where build target formed the first level)
            builds = os.listdir(buildstat_subdir)
            if re.match('^20[0-9]{10,12}$', builds[-1]):
                if len(builds) != 1:
                    log.warn('%s in %s contains multiple builds, using only %s',
                             os.path.join(os.path.basename(buildstat_dir), buildstat_subdir), archive,
                             os.path.basename(buildstat_subdir))
                buildstat_subdir = os.path.join(buildstat_subdir, builds[-1])

            shutil.move(buildstat_subdir, buildstat_dir)
            shutil.rmtree(buildstat_tmpdir)

        # Check if the file hierarchy is 'old style'
        converted = False
        if os.path.exists(os.path.join(results_dir, 'output.log')) and convert:
            log.info("Converting test results from %s", archive_fn)
            try:
                convert_results(poky_repo, results_dir, fn_fields['host'])
                converted = True
            except ConversionError as err:
                log.warn("Skipping %s, conversion failed: %s", archive_fn, err)
                return False, str(err)
        else:
            log.info('Importing test results from %s', archive)

        # Get info for git branch and tag names
        fmt_fields = {'host': fn_fields['host'],
                      'product': 'poky',
                      'branch': None,
                      'rev': None,
                      'machine': 'qemux86',
                      'rev_cnt': None}

        if os.path.exists(os.path.join(results_dir, 'results.json')):
            with open(os.path.join(results_dir, 'results.json')) as fobj:
                data = json.load(fobj)
            fmt_fields['host'] = data['tester_host']
            fmt_fields['branch'] = data['git_branch']
            fmt_fields['rev'] = data['git_commit']
            fmt_fields['rev_cnt'] = data['git_commit_count']
        else:
            out_log = OutputLog(os.path.join(results_dir, 'output.log'))
            fmt_fields['branch'], fmt_fields['rev'] = \
                    out_log.get_git_rev_info()
            cmd = ['rev-list', '--count', fmt_fields['rev'], '--']
            fmt_fields['rev_cnt'] = poky_repo.run_cmd(cmd).splitlines()[0]

        # Compose git branch and tag name
        git_branch = branch_fmt % fmt_fields
        git_tag = tag_fmt % fmt_fields
        tag_cnt = len(data_repo.run_cmd(['tag', '-l', git_tag + '/*']).splitlines())
        git_tag += '/%d' % tag_cnt

        # Get timestamp for commit and tag
        timestamp = datetime.strptime(fn_fields['timestamp'], '%Y%m%d%H%M%S')
        git_timestamp = "%d" % time.mktime(timestamp.timetuple())

        # Commit to git
        commit_msg = "Results of {}:{} on {}\n\n".format(
            fmt_fields['branch'], fmt_fields['rev'], fmt_fields['host'])

        if os.path.isdir(archive):
            archive_fn += '/'
        if converted:
            commit_msg += "(converted from {})".format(archive_fn)
        else:
            commit_msg += "(imported from {})".format(archive_fn)

        tag_msg = "Test run #{} of {}:{} on {}\n".format(
                tag_cnt, fmt_fields['branch'], fmt_fields['rev'],
                fmt_fields['host'])
        git_commit_dir(data_repo, results_dir, git_branch, commit_msg,
                       git_tag, tag_msg, git_timestamp)
    finally:
        shutil.rmtree(tmpdir)
    return True, "OK"


def read_globalres(path):
    """Read globalres file"""
    # Read globalres.log
    globalres = defaultdict(list)

    log.info("Reading '%s'", path)
    with open(path) as fobj:
        reader = csv.reader(fobj)
        for row in reader:
            # Skip manually added comments
            if row[0].startswith('#'):
                continue
            res = {'host': row[0]}
            res['branch'], res['revision'] = row[1].split(':')
            if len(row) == 12:
                res['times'] = row[3:10]
                res['sizes'] = row[10:]
            elif len(row) == 14 or len(row) == 15:
                res['times'] = row[3:11]
                res['sizes'] = row[11:]
            else:
                log.warning("globalres: ignoring invalid row that contains "
                            "%s values: %s", len(row), row)
            globalres[res['revision']].append(res)
    return globalres


def get_archive_timestamp(filename):
    """Helper for sorting result tarballs"""
    split = os.path.basename(filename).rsplit('-', 2)
    if len(split) == 4:
        return split[3].split('.')[0]
    else:
        return split[2]


def parse_args(argv=None):
    """Parse command line arguments"""
    parser = argparse.ArgumentParser()

    parser.add_argument('-d', '--debug', action='store_true',
                        help='Debug level logging')
    parser.add_argument('-B', '--git-branch-name',
                        default='%(host)s/%(branch)s/%(machine)s',
                        help="Branch name to use")
    parser.add_argument('-T', '--git-tag-name',
                        default='%(host)s/%(branch)s/%(machine)s/%(rev_cnt)s-g%(rev)s',
                        help="Tag 'basename' to use, tag number will be "
                             "automatically appended")
    parser.add_argument('-c', '--convert', action='store_true',
                        help="Convert results to new JSON-based format")
    parser.add_argument('-P', '--poky-git', type=os.path.abspath, required=True,
                        help="Path to poky clone")
    parser.add_argument('-g', '--git-dir', type=os.path.abspath, required=True,
                        help="Git repository where to commit results")
    parser.add_argument('archive', nargs="+", type=os.path.abspath,
                        help="Results archive")
    args = parser.parse_args()

    return args


def main(argv=None):
    """Script entry point"""
    args = parse_args(argv)
    if args.debug:
        log.setLevel(logging.DEBUG)

    ret = 1
    try:
        # Check archives to be imported
        for archive in args.archive:
            if not os.path.exists(archive):
                raise CommitError("File does not exist: {}".format(archive))

        # Check Poky repo
        poky_repo = GitRepo(args.poky_git, is_topdir=True)

        # Check results repository
        if not os.path.exists(args.git_dir):
            log.info('Creating Git repository %s', args.git_dir)
            os.mkdir(args.git_dir)
            data_repo = GitRepo.init(args.git_dir)
        else:
            data_repo = GitRepo(args.git_dir, is_topdir=True)

        # Import archived results
        imported = []
        skipped = []
        for archive in sorted(args.archive, key=get_archive_timestamp):
            result = import_testrun(archive, data_repo, poky_repo,
                                    args.git_branch_name, args.git_tag_name,
                                    args.convert)
            if result[0]:
                imported.append(result[1])
            else:
                skipped.append((archive, result[1]))

        log.debug('Resetting git worktree')
        data_repo.run_cmd(['reset', '--hard', 'HEAD', '--'])
        data_repo.run_cmd(['clean', '-fd'])

        print("\nSuccessfully imported {} archived results".format(len(imported)))
        if skipped:
            print("Failed to import {} result archives:".format(len(skipped)))
            for archive, reason in skipped:
                print("    {}: {}".format(archive, reason))

        ret = 0
    except CommitError as err:
        if len(str(err)) > 0:
            log.error(str(err))

    return ret

if __name__ == '__main__':
    sys.exit(main())
