# LTP runtime
#
# Copyright (c) 2019 MontaVista Software, LLC
#
# SPDX-License-Identifier: GPL-2.0-only
#

import time
import datetime
import pprint

from oeqa.core.decorator.depends import OETestDepends
from oeqa.runtime.decorator.package import OEHasPackage
from oeqa.utils.logparser import LtpParser
from oeqa.utils.ltp import LtpTestBase

class LtpTest(LtpTestBase):

    ltp_groups = ["math", "syscalls", "dio", "io", "mm", "ipc", "sched", "nptl", "pty", "containers", "controllers", "filecaps", "cap_bounds", "fcntl-locktests", "connectors","timers", "commands", "net.ipv6_lib", "input","fs_perms_simple"]

    ltp_fs = ["fs", "fsx", "fs_bind", "fs_ext4"]
    # skip kernel cpuhotplug
    ltp_kernel = ["power_management_tests", "hyperthreading ", "kernel_misc", "hugetlb"]
    ltp_groups += ltp_fs

    # LTP runtime tests
    @OETestDepends(['ssh.SSHTest.test_ssh'])
    @OEHasPackage(["ltp"])
    def test_ltp_help(self):
        (status, output) = self.target.run('/opt/ltp/runltp --help')
        msg = 'Failed to get ltp help. Output: %s' % output
        self.assertEqual(status, 0, msg=msg)

    @OETestDepends(['ltp.LtpTest.test_ltp_help'])
    def test_ltp_groups(self):
        for ltp_group in self.ltp_groups: 
            self.cmd = '/opt/ltp/runltp -f %s -p -q -r /opt/ltp -l /opt/ltp/results/%s -I 1 -d /opt/ltp' % (ltp_group, ltp_group)
            self.runltp(ltp_group, 'ltpresult')

    @OETestDepends(['ltp.LtpTest.test_ltp_groups'])
    def test_ltp_runltp_cve(self):
        self.cmd = '/opt/ltp/runltp -f cve -p -q -r /opt/ltp -l /opt/ltp/results/cve -I 1 -d /opt/ltp'
        self.runltp('cve')
