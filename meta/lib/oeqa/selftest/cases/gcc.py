# SPDX-License-Identifier: MIT
import os
from oeqa.selftest.case import OESelftestTestCase
from oeqa.utils.commands import bitbake, get_bb_var, get_bb_vars, runqemu, Command

def parse_values(content):
    for i in content:
        for v in ["PASS", "FAIL", "XPASS", "XFAIL", "UNRESOLVED", "UNSUPPORTED", "UNTESTED", "ERROR", "WARNING"]:
            if i.startswith(v + ": "):
                yield i[len(v) + 2:].strip(), v
                break

class GccSelfTest(OESelftestTestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        if not hasattr(cls.tc, "extraresults"):
            cls.tc.extraresults = {}

    def gcc_runtime_check_skip(self, suite):
        targets = get_bb_var("RUNTIMETARGET", "gcc-runtime").split()
        if suite not in targets:
            self.skipTest("Target does not use {0}".format(suite))

    def test_cross_gcc(self):
        self.gcc_cross_run_check("gcc")

    def test_cross_gxx(self):
        self.gcc_cross_run_check("g++")

    def test_gcc_runtime_libatomic(self):
        self.gcc_runtime_run_check("libatomic")

    def test_gcc_runtime_libgomp(self):
        self.gcc_runtime_run_check("libgomp")

    def test_gcc_runtime_libstdcxx(self):
        self.gcc_runtime_run_check("libstdc++-v3")

    def test_gcc_runtime_libssp(self):
        self.gcc_runtime_check_skip("libssp")
        self.gcc_runtime_run_check("libssp")

    def test_gcc_runtime_libitm(self):
        self.gcc_runtime_check_skip("libitm")
        self.gcc_runtime_run_check("libitm")

    def gcc_cross_run_check(self, suite):
        return self.gcc_run_check("gcc-cross-{0}".format(get_bb_var("TUNE_ARCH")), suite)

    def gcc_runtime_run_check(self, suite):
        return self.gcc_run_check("gcc-runtime", suite, target_prefix = "check-target-")

    def gcc_run_check(self, recipe, suite, target_prefix = "check-", ssh = None):
        target = target_prefix + suite.replace("gcc", "gcc").replace("g++", "c++")

        # configure ssh target
        features = []
        features.append('MAKE_CHECK_TARGETS = "{0}"'.format(target))
        if ssh is not None:
            features.append('BUILD_TEST_TARGET = "ssh"')
            features.append('BUILD_TEST_HOST = "{0}"'.format(ssh))
            features.append('BUILD_TEST_HOST_USER = "root"')
            features.append('BUILD_TEST_HOST_PORT = "22"')
        self.write_config("\n".join(features))

        bitbake("{0} -c check".format(recipe))

        bb_vars = get_bb_vars(["TUNE_ARCH", "B", "TARGET_SYS"], recipe)
        tune_arch, builddir, target_sys = bb_vars["TUNE_ARCH"], bb_vars["B"], bb_vars["TARGET_SYS"]

        sumspath = os.path.join(builddir, "gcc", "testsuite", suite, "{0}.sum".format(suite))
        if not os.path.exists(sumspath): # check in target dirs
            sumspath = os.path.join(builddir, target_sys, suite, "testsuite", "{0}.sum".format(suite))
        if not os.path.exists(sumspath): # handle libstdc++-v3 -> libstdc++
            sumspath = os.path.join(builddir, target_sys, suite, "testsuite", "{0}.sum".format(suite.split("-")[0]))

        failed = 0
        with open(sumspath, "r") as f:
            for test, result in parse_values(f):
                self.tc.extraresults["{}.{}.{}".format(type(self).__name__, suite, test)] = {"status" : result}
                if result == "FAIL":
                    self.logger.info("failed: '{}'".format(test))
                    failed += 1
        self.assertEqual(failed, 0)

class GccSelfTestSystemEmulated(GccSelfTest):
    default_installed_packages = ["libgcc", "libstdc++", "libatomic", "libgomp"]

    def gcc_run_check(self, *args, **kwargs):
        tune_arch = get_bb_var("TUNE_ARCH")

        # build core-image-minimal with required packages
        features = []
        features.append('IMAGE_FEATURES += "ssh-server-openssh"')
        features.append('CORE_IMAGE_EXTRA_INSTALL += "{0}"'.format(" ".join(self.default_installed_packages)))
        self.write_config("\n".join(features))
        bitbake("core-image-minimal")

        # wrap the execution with a qemu instance
        with runqemu("core-image-minimal", runqemuparams = "nographic") as qemu:
            # validate that SSH is working
            status, _ = qemu.run("uname")
            self.assertEqual(status, 0)

            return super().gcc_run_check(*args, **kwargs, ssh = qemu.ip)

