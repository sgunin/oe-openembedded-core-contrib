SUMMARY = "Package maintenance system from Debian"
LICENSE = "GPLv2.0+"
SECTION = "base"
DEPENDS = "perl"
DEPENDS_append_class-native = " bzip2-replacement-native ${@bb.utils.contains('PACKAGECONFIG', 'update-alternatives', '', 'virtual/update-alternatives-native', d)}"
PROVIDES = "${@bb.utils.contains('PACKAGECONFIG', 'update-alternatives', 'virtual/update-alternatives', '', d)}"

UPSTREAM_CHECK_URI = "${DEBIAN_MIRROR}/main/d/dpkg/"

inherit autotools gettext perlnative pkgconfig systemd perl-version

python () {
    if not bb.utils.contains('DISTRO_FEATURES', 'sysvinit', True, False, d):
        pn = d.getVar('PN')
        d.setVar('SYSTEMD_SERVICE_%s' % (pn), 'dpkg-configure.service')
}

export PERL = "${bindir}/perl"
PERL_class-native = "${STAGING_BINDIR_NATIVE}/perl-native/perl"

export PERL_LIBDIR = "${libdir}/perl/${@get_perl_version(d)}"
PERL_LIBDIR_class-native = "${libdir}/perl-native/perl/${@get_perl_version(d)}"

UA = "update-alternatives"
UA_native = "${@['', 'update-alternatives'][d.getVar('PREFERRED_PROVIDER_virtual/update-alternatives-native', True) == 'dpkg-native']}"

PACKAGECONFIG ??= "bzip2 gzip start-stop-daemon ${UA}"
PACKAGECONFIG[bzip2] = "--with-libbz2,--without-libbz2,bzip2"
PACKAGECONFIG[dselect] = "--enable-dselect,--disable-dselect,ncurses"
PACKAGECONFIG[gzip] = "--with-libz,--without-libz,zlib"
PACKAGECONFIG[selinux] = "--with-libselinux,--without-libselinux,libselinux"
PACKAGECONFIG[start-stop-daemon] = "--enable-start-stop-daemon,--disable-start-stop-daemon"
PACKAGECONFIG[update-alternatives] = "--enable-update-alternatives,--disable-update-alternatives"
PACKAGECONFIG[xz] = "--with-liblzma,--without-liblzma,xz"

DPKG_DEB_COMPRESSOR ?= "${@bb.utils.contains('PACKAGECONFIG', 'xz', 'xz', 'gzip', d)}"

EXTRA_OECONF += "--with-dpkg-deb-compressor=${DPKG_DEB_COMPRESSOR}"

PACKAGECONFIG = "liblzma"
PACKAGECONFIG[liblzma] = "--with-liblzma,--without-liblzma, xz"

EXTRA_OECONF += "TAR=tar"
EXTRA_OECONF_append_class-target = " DEB_HOST_ARCH=${DPKG_ARCH}"

do_configure_prepend_class-native() {
    sed -i -e 's|SYSCONFDIR|"${sysconfdir_native}"|' ${S}/utils/update-alternatives.c
}

do_install_append () {
	if [ "${PN}" = "dpkg-native" ]; then
		sed -i -e 's|^#!.*${bindir}/perl-native.*/perl|#!/usr/bin/env nativeperl|' ${D}${bindir}/dpkg-*
	else
		sed -i -e 's|^#!.*${bindir}/perl-native.*/perl|#!/usr/bin/env perl|' ${D}${bindir}/dpkg-*
	fi

	if ${@bb.utils.contains('DISTRO_FEATURES','sysvinit','false','true',d)};then
		install -d ${D}${systemd_unitdir}/system
		install -m 0644 ${WORKDIR}/dpkg-configure.service ${D}${systemd_unitdir}/system/
		sed -i -e 's,@BASE_BINDIR@,${base_bindir},g' \
			-e 's,@SYSCONFDIR@,${sysconfdir},g' \
			-e 's,@BINDIR@,${bindir},g' \
			-e 's,@SYSTEMD_UNITDIR@,${systemd_unitdir},g' \
			${D}${systemd_unitdir}/system/dpkg-configure.service
	fi
}

PACKAGES =+ "update-alternatives-dpkg"

RDEPENDS_${PN} = "${VIRTUAL-RUNTIME_update-alternatives} perl"
RDEPENDS_${PN}_class-native = ""

FILES_update-alternatives-dpkg = "${bindir}/update-alternatives ${localstatedir}/lib/dpkg/alternatives ${sysconfdir}/alternatives"
RPROVIDES_update-alternatives-dpkg = "update-alternatives"
RCONFLICTS_update-alternatives-dpkg = "update-alternatives"

PACKAGES += "${PN}-perl"
FILES_${PN}-perl = "${libdir}/perl/${@get_perl_version(d)}"

RDEPENDS_${PN}-perl += "perl-module-carp perl-module-constant \
                        perl-module-cwd perl-module-digest \
                        perl-module-digest-md5 perl-module-errno \
                        perl-module-exporter perl-module-fcntl \
                        perl-module-feature perl-module-file-basename \
                        perl-module-file-compare perl-module-file-copy \
                        perl-module-file-find perl-module-file-path \
                        perl-module-file-spec perl-module-file-temp \
                        perl-module-list-util perl-module-overload \
                        perl-module-parent perl-module-storable \
                        perl-module-filehandle perl-module-io \
                        perl-module-io-handle perl-module-io-seekable \
                        perl-module-posix perl-module-scalar-util \
                        perl-module-selectsaver perl-module-symbol \
                        perl-module-term-ansicolor perl-module-tie-handle \
                        perl-module-tie-hash perl-module-storable \
                        perl-module-time-hires perl-module-time-piece \
                        perl-module-xsloader"

# Split out start-stop-daemon to its own package.  Note that it
# is installed in a different directory than the one used for
# the bitbake version.
#
PACKAGES =+ "${PN}-start-stop"
FILES_${PN}-start-stop = "${sbindir}/start-stop-daemon"

RDEPENDS_${PN} += "${PN}-start-stop"


BBCLASSEXTEND = "native"
