# LTP base 
#
# Copyright (c) 2019 MontaVista Software, LLC
#
# SPDX-License-Identifier: GPL-2.0-only
#

import time
import datetime
import pprint

from oeqa.runtime.case import OERuntimeTestCase

class LtpBase(OERuntimeTestCase):
    '''
    Base routines for LTP based testing
    '''

    @classmethod
    def setUpClass(cls):
        cls.ltp_startup()

    @classmethod
    def tearDownClass(cls):
        cls.ltp_finishup()

    @classmethod
    def ltp_startup(cls):
        cls.sections = {}
        cls.failmsg = ""
        cls.cmd = ""
        test_log_dir = os.path.join(cls.td.get('WORKDIR', ''), 'testimage')
        timestamp = datetime.datetime.now().strftime('%Y%m%d%H%M%S')

        cls.ltptest_log_dir_link = os.path.join(test_log_dir, 'ltp_log')
        cls.ltptest_log_dir = '%s.%s' % (cls.ltptest_log_dir_link, timestamp)
        os.makedirs(cls.ltptest_log_dir)

        cls.tc.target.run("mkdir -p /opt/ltp/results")

        if not hasattr(cls.tc, "extraresults"):
            cls.tc.extraresults = {}
        cls.extras = cls.tc.extraresults
        cls.extras['ltpresult.rawlogs'] = {'log': ""}

    @classmethod
    def runltp(cls, ltp_group):
        starttime = time.time()
        (status, output) = cls.tc.target.run(cls.cmd)
        endtime = time.time()

        with open(os.path.join(cls.ltptest_log_dir, "%s-raw.log" % ltp_group), 'w') as f:
            f.write(output)

        cls.extras['ltpresult.rawlogs']['log'] = cls.extras['ltpresult.rawlogs']['log'] + output

        # copy nice log from DUT
        dst = os.path.join(cls.ltptest_log_dir, "%s" %  ltp_group )
        remote_src = "/opt/ltp/results/%s" % ltp_group 
        (status, output) = cls.tc.target.copyFrom(remote_src, dst)
        msg = 'File could not be copied. Output: %s' % output
        self.assertEqual(status, 0, msg=msg)

        parser = LtpParser()
        results, sections  = parser.parse(dst)

        runtime = int(endtime-starttime)
        sections['duration'] = runtime
        cls.sections[ltp_group] =  sections

        failed_tests = {}
        for test in results:
            result = results[test]
            testname = (logname + "." + ltp_group + "." + test)
            cls.extras[testname] = {'status': result}
            if result == 'FAILED':
                failed_tests[ltp_group] = test 

        if failed_tests:
            cls.failmsg = cls.failmsg + "Failed ptests:\n%s" % pprint.pformat(failed_tests)

class LtpTestBase(LtpBase):
    '''
    Ltp normal section definition
    '''
    @classmethod
    def ltp_finishup(cls):
        cls.extras['ltpresult.sections'] =  cls.sections

        # update symlink to ltp_log
        if os.path.exists(cls.ltptest_log_dir_link):
            os.remove(cls.ltptest_log_dir_link)
        os.symlink(os.path.basename(cls.ltptest_log_dir), cls.ltptest_log_dir_link)

        if cls.failmsg:
            cls.fail(cls.failmsg)

class LtpPosixBase(LtpBase):
    '''
    Ltp Posix section definition
    '''

    @classmethod
    def ltp_finishup(cls):
        cls.extras['ltpposixresult.sections'] =  cls.sections

        # update symlink to ltp_log
        if os.path.exists(cls.ltptest_log_dir_link):
            os.remove(cls.ltptest_log_dir_link)

        os.symlink(os.path.basename(cls.ltptest_log_dir), cls.ltptest_log_dir_link)

        if cls.failmsg:
            cls.fail(cls.failmsg)


class LtpStressBase(LtpBase):
    '''
    Ltp Stress test section definition
    '''

    @classmethod
    def ltp_finishup(cls):
        cls.extras['ltpstressresult.sections'] =  cls.sections

        # update symlink to ltp_log
        if os.path.exists(cls.ltptest_log_dir_link):
            os.remove(cls.ltptest_log_dir_link)

        os.symlink(os.path.basename(cls.ltptest_log_dir), cls.ltptest_log_dir_link)

        if cls.failmsg:
            cls.fail(cls.failmsg)
