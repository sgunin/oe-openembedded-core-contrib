# LSB compliance runtime
#
# Copyright (c) 2019 MontaVista Software, LLC
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

import time
import datetime
import bb

from oeqa.runtime.case import OERuntimeTestCase
from oeqa.core.decorator.depends import OETestDepends
from oeqa.runtime.decorator.package import OEHasPackage

class LSBTest(OERuntimeTestCase):

    @OETestDepends(['ssh.SSHTest.test_ssh'])
    @OEHasPackage(["lsbtest", 'rpm', 'perl-mouldes'])
    def test_lsb_test(self):
        bb.warn("lsb-test")
        cmd = "sh /opt/lsb-test/LSB_Test.sh"
        bb.warn("%s" % cmd)
        starttime = time.time()
        (status, output) = self.target.run(cmd, 100)
        endtime = time.time()
        runtime = int(endtime - starttime)
        bb.warn("lsb: %s %s" % (output, runtime))
