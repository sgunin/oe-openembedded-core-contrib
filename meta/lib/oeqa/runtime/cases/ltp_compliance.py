# LTP compliance runtime
#
# Copyright (c) 2019 MontaVista Software, LLC
#
# SPDX-License-Identifier: GPL-2.0-only
#

import time
import datetime
import pprint

from oeqa.runtime.case import OERuntimeTestCase
from oeqa.core.decorator.depends import OETestDepends
from oeqa.runtime.decorator.package import OEHasPackage
from oeqa.utils.logparser import LtpComplianceParser
from oeqa.utils.ltp import LtpPosixBase

class LtpPosixTest(LtpPosixBase):
    posix_groups = ["AIO", "MEM", "MSG", "SEM", "SIG",  "THR", "TMR", "TPS"]

    # LTP Posix compliance runtime tests

    @OETestDepends(['ssh.SSHTest.test_ssh'])
    @OEHasPackage(["ltp"])
    def test_posix_groups(self):
        for posix_group in self.posix_groups: 
            self.cmd = "/opt/ltp/bin/run-posix-option-group-test.sh %s 2>@1 | tee /opt/ltp/results/%s" % (posix_group, posix_group)
            self.runltp(posix_group)
