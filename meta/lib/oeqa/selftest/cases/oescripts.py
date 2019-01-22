import os, sys
from oeqa.selftest.case import OESelftestTestCase
from oeqa.core.decorator.depends import OETestDepends
from oeqa.selftest.cases.buildhistory import BuildhistoryBase
from oeqa.utils.commands import Command, runCmd, bitbake, get_bb_var, get_test_layer
from oeqa.core.decorator.oeid import OETestID


class BuildhistoryDiffTests(BuildhistoryBase):

    @OETestID(295)
    def test_buildhistory_diff(self):
        target = 'xcursor-transparent-theme'
        self.run_buildhistory_operation(target, target_config="PR = \"r1\"", change_bh_location=True)
        self.run_buildhistory_operation(target, target_config="PR = \"r0\"", change_bh_location=False, expect_error=True)
        result = runCmd("oe-pkgdata-util read-value PKGV %s" % target)
        pkgv = result.output.rstrip()
        result = runCmd("buildhistory-diff -p %s" % get_bb_var('BUILDHISTORY_DIR'))
        expected_endlines = [
            "xcursor-transparent-theme-dev: RDEPENDS: removed \"xcursor-transparent-theme (['= %s-r1'])\", added \"xcursor-transparent-theme (['= %s-r0'])\"" % (pkgv, pkgv),
            "xcursor-transparent-theme-staticdev: RDEPENDS: removed \"xcursor-transparent-theme-dev (['= %s-r1'])\", added \"xcursor-transparent-theme-dev (['= %s-r0'])\"" % (pkgv, pkgv)
        ]
        for line in result.output.splitlines():
            for el in expected_endlines:
                if line.endswith(el):
                    expected_endlines.remove(el)
                    break
            else:
                self.fail('Unexpected line:\n%s\nExpected line endings:\n  %s' % (line, '\n  '.join(expected_endlines)))
        if expected_endlines:
            self.fail('Missing expected line endings:\n  %s' % '\n  '.join(expected_endlines))


class OEScriptTests(OESelftestTestCase):
    def check_endlines(self, results,  expected_endlines): 
        for line in results.output.splitlines():
            for el in expected_endlines:
                if line == el:
                    expected_endlines.remove(el)
                    break

        if expected_endlines:
            self.fail('Missing expected line endings:\n  %s' % '\n  '.join(expected_endlines))


class OEPybootchartguyTests(OEScriptTests):
    def test_pybootchartguy_help(self):
        runCmd('../scripts/pybootchartgui/pybootchartgui.py  --help')

    def test_pybootchartguy_to_generate_build_png_output(self):
        tmpdir = get_bb_var('TMPDIR')
        runCmd('../scripts/pybootchartgui/pybootchartgui.py  %s/buildstats -o %s/charts -f png' % (tmpdir, tmpdir))

    def test_pybootchartguy_to_generate_build_svg_output(self):
        tmpdir = get_bb_var('TMPDIR')
        runCmd('../scripts/pybootchartgui/pybootchartgui.py  %s/buildstats -o %s/charts -f svg' % (tmpdir, tmpdir))

    def test_pybootchartguy_to_generate_build_pdf_output(self):
        tmpdir = get_bb_var('TMPDIR')
        runCmd('../scripts/pybootchartgui/pybootchartgui.py  %s/buildstats -o %s/charts -f pdf' % (tmpdir, tmpdir))


class OEListPackageconfigTests(OEScriptTests):
    #oe-core.scripts.List_all_the_PACKAGECONFIG's_flags
    def test_packageconfig_flags_help(self):
        runCmd('../scripts/contrib/list-packageconfig-flags.py -h')

    def test_packageconfig_flags_default(self):
        results = runCmd('../scripts/contrib/list-packageconfig-flags.py')
        expected_endlines = []
        expected_endlines.append("RECIPE NAME                                                                    PACKAGECONFIG FLAGS")
        expected_endlines.append("xserver-xorg-1.20.3                                                            dri dri2 dri3 gcrypt glamor glx nettle openssl systemd systemd-logind udev unwind xinerama xmlto xshmfence xwayland")
        expected_endlines.append("znc-1.7.1                                                                      ipv6")

        self.check_endlines(results, expected_endlines)


    def test_packageconfig_flags_option_flags(self):
        results = runCmd('../scripts/contrib/list-packageconfig-flags.py -f')
        expected_endlines = []
        expected_endlines.append("PACKAGECONFIG FLAG     RECIPE NAMES")
        expected_endlines.append("xshmfence              xserver-xorg-1.20.3")

        self.check_endlines(results, expected_endlines)

    def test_packageconfig_flags_option_all(self):
        results = runCmd('../scripts/contrib/list-packageconfig-flags.py -a')
        expected_endlines = []
        expected_endlines.append("xserver-xorg-1.20.3")
        expected_endlines.append("PACKAGECONFIG dri2 udev openssl")
        expected_endlines.append("PACKAGECONFIG[udev] --enable-config-udev,--disable-config-udev,udev")
        expected_endlines.append("PACKAGECONFIG[dri] --enable-dri,--disable-dri,xorgproto virtual/mesa")
        expected_endlines.append("PACKAGECONFIG[dri2] --enable-dri2,--disable-dri2,xorgproto")
        expected_endlines.append("PACKAGECONFIG[dri3] --enable-dri3,--disable-dri3,xorgproto")
        expected_endlines.append("PACKAGECONFIG[glx] --enable-glx,--disable-glx,xorgproto virtual/libgl virtual/libx11")
        expected_endlines.append("PACKAGECONFIG[glamor] --enable-glamor,--disable-glamor,libepoxy virtual/libgbm,libegl")
        expected_endlines.append("PACKAGECONFIG[unwind] --enable-libunwind,--disable-libunwind,libunwind")
        expected_endlines.append("PACKAGECONFIG[xshmfence] --enable-xshmfence,--disable-xshmfence,libxshmfence")
        expected_endlines.append("PACKAGECONFIG[xmlto] --with-xmlto, --without-xmlto, xmlto-native docbook-xml-dtd4-native docbook-xsl-stylesheets-native")
        expected_endlines.append("PACKAGECONFIG[systemd-logind] --enable-systemd-logind=yes,--enable-systemd-logind=no,dbus,")
        expected_endlines.append("PACKAGECONFIG[systemd] --with-systemd-daemon,--without-systemd-daemon,systemd")
        expected_endlines.append("PACKAGECONFIG[xinerama] --enable-xinerama,--disable-xinerama,xorgproto")
        expected_endlines.append("PACKAGECONFIG[xwayland] --enable-xwayland,--disable-xwayland,wayland wayland-native wayland-protocols libepoxy")
        expected_endlines.append("PACKAGECONFIG[openssl] --with-sha1=libcrypto,,openssl")
        expected_endlines.append("PACKAGECONFIG[nettle] --with-sha1=libnettle,,nettle")
        expected_endlines.append("PACKAGECONFIG[gcrypt] --with-sha1=libgcrypt,,libgcrypt")

        self.check_endlines(results, expected_endlines)

    def test_packageconfig_flags_optiins_preferred_only(self):
        results = runCmd('../scripts/contrib/list-packageconfig-flags.py -p')
        expected_endlines = []
        expected_endlines.append("RECIPE NAME                                      PACKAGECONFIG FLAGS")
        expected_endlines.append("xserver-xorg-1.20.3                              dri dri2 dri3 gcrypt glamor glx nettle openssl systemd systemd-logind udev unwind xinerama xmlto xshmfence xwayland")

        self.check_endlines(results, expected_endlines)

