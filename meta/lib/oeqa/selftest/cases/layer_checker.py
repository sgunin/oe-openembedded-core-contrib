#
# SPDX-License-Identifier: MIT
#

import os
import shutil
import unittest
from oeqa.selftest.case import OESelftestTestCase
from oeqa.utils.commands import Command, runCmd, bitbake, get_bb_var
from oeqa.utils import CommandError

YCL_WHITELIST = "meta"

class YoctoCheckLayerBase(OESelftestTestCase):
    test_distro_features_add = "pam systemd opengl ptest"
    test_distro_features_rm = "x11 ipv6"
    scripts_dir = os.path.join(get_bb_var('COREBASE'), 'scripts')
    layers = get_bb_var("BBLAYERS").split()

    check_layers = ""
    opts = "-n "

    for layer in layers:
        if os.path.basename(layer) not in YCL_WHITELIST:
            check_layers  += " %s" % layer

    def run_check_layer(self, features):
            self.write_config(features)
            result = runCmd("%s/yocto-check-layer %s %s" % (self.scripts_dir, self.opts, self.check_layers) )
            self.remove_config(features)

class YoctoCheckLayerTests(YoctoCheckLayerBase):

    def test_yocto_check_layer_distro_default(self):
        self.logger.info("Defatult layer check")
        result = runCmd("%s/yocto-check-layer %s %s" % (self.scripts_dir, self.opts, self.check_layers) )

class YoctoCheckLayerDistroTests(YoctoCheckLayerBase):
    def check_layer_features_list(self):
        for feature in self.test_distro_features_add.split():
            self.logger.info("layer check with '%s' enabled" % feature)
            features = 'DISTRO_FEATURES_append = " %s" \n' % feature
            self.run_check_layer(features)

    def test_yocto_check_layer_distro_features_add(self):
        self.check_layer_features_list()

    def test_yocto_check_layer_distro_nox11(self):
        features = 'DISTRO_FEATURES_remove = "x11"\n'
        self.run_check_layer(features)

    def test_yocto_check_layer_distro_nopipv6(self):
        features = 'DISTRO_FEATURES_remove = "ipv6"\n'
        self.run_check_layer(features)



