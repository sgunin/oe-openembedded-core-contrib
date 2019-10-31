# LTP Stress runtime
#
# Copyright (c) 2019 MontaVista Software, LLC
#
# SPDX-License-Identifier: MIT
#

import time
import datetime
import bb

from oeqa.core.decorator.depends import OETestDepends
from oeqa.runtime.decorator.package import OEHasPackage
from oeqa.core.decorator.data import skipIfQemu
from oeqa.utils.ltp import LtpStressBase

class LtpStressTest(LtpStressBase):
    # LTP stress runtime tests
    # crashme [NBYTES] [SRAND] [NTRYS] [NSUB] [VERBOSE]
    #

    @skipIfQemu('qemuall', 'Test only runs on real hardware')

    @OETestDepends(['ssh.SSHTest.test_ssh'])
    @OEHasPackage(["ltp"])
    def test_ltp_stress(self):
        self.tc.target.run("sed -i -r 's/^fork12.*//' /opt/ltp/runtest/crashme")
        self.cmd = '/opt/ltp/runltp -f crashme +2000 666 100 0:0:00 -p -q -r /opt/ltp -l /opt/ltp/results/crash -I 1 -d /opt/ltp '
        self.runltp('crashme')
