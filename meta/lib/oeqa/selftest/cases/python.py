import os
import shutil

from oeqa.selftest.case import OESelftestTestCase
from oeqa.utils.commands import bitbake, get_bb_vars, runCmd


class PythonTests(OESelftestTestCase):

    def test_python_pgo(self):
        bbvars = get_bb_vars(['TMPDIR', 'MACHINE'])
        profile_dir = os.path.join(bbvars['TMPDIR'], 'oeqa', bbvars['MACHINE'], 'python3-pgo-data')

        self.write_config("""
PYTHON3_PROFILE_OPT = "1"
PYTHON3_PROFILE_TASK = "-m test.regrtest test_abc"
PYTHON3_PROFILE_DIR = "{}"
""".format(profile_dir))

        profile_file = os.path.join(profile_dir, 'Objects', 'object.gcda')

        bitbake('python-pgo-image -ccleansstate')
        bitbake('python-pgo-image -cprofile')

        # Check that profile task generated a file
        self.assertTrue(os.path.isfile(profile_file))

        # Check that python builds with pgo enabled
        bitbake('python3')
