SUMMARY = "Package maintenance system from Debian"
SECTION = "base"
LICENSE = "GPLv2+"
DEPENDS = "perl"
DEPENDS_append_class-native = " bzip2-replacement-native"
PROVIDES = "virtual/update-alternatives"

SRC_URI_append_class-native = " file://0001-When-running-do_package_write_deb-we-have-trees-of-h.patch"

UPSTREAM_CHECK_URI = "${DEBIAN_MIRROR}/main/d/dpkg/"

inherit autotools gettext perlnative pkgconfig systemd

python () {
    if not bb.utils.contains('DISTRO_FEATURES', 'sysvinit', True, False, d):
        pn = d.getVar('PN', True)
        d.setVar('SYSTEMD_SERVICE_%s' % (pn), 'dpkg-configure.service')
}

export PERL = "${bindir}/perl"
PERL_class-native = "${STAGING_BINDIR_NATIVE}/perl-native/perl"

export PERL_LIBDIR = "${libdir}/perl"
PERL_LIBDIR_class-native = "${libdir}/perl-native/perl"

PACKAGECONFIG ??= "bzip2 gzip start-stop-daemon update-alternatives"
PACKAGECONFIG[bzip2] = "--with-bz2,--without-bz2,bzip2"
PACKAGECONFIG[dselect] = "--enable-dselect,--disable-dselect,ncurses"
PACKAGECONFIG[gzip] = "--with-zlib,--without-zlib,zlib"
PACKAGECONFIG[selinux] = "--with-selinux,--without-selinux,libselinux"
PACKAGECONFIG[start-stop-daemon] = "--enable-start-stop-daemon,--disable-start-stop-daemon"
PACKAGECONFIG[update-alternatives] = "--enable-update-alternatives,--disable-update-alternatives"
PACKAGECONFIG[xz] = "--with-liblzma,--without-liblzma,xz"

DPKG_DEB_COMPRESSOR ?= "${@base_contains('PACKAGECONFIG', 'xz', 'xz', 'gzip', d)}"

EXTRA_OECONF += "--with-dpkg-deb-compressor=${DPKG_DEB_COMPRESSOR}"

EXTRA_OECONF += "TAR=tar"
EXTRA_OECONF += "DEB_HOST_ARCH=${DPKG_ARCH}"

do_configure_prepend_class-native() {
    sed -i -e 's|SYSCONFDIR|"/etc"|' ${S}/utils/update-alternatives.c
}

do_configure () {
    echo >> ${S}/m4/compiler.m4
    sed -i -e 's#PERL_LIBDIR=.*$#PERL_LIBDIR="${libdir}/perl"#' ${S}/configure
    autotools_do_configure
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

RDEPENDS_${PN} = "${VIRTUAL-RUNTIME_update-alternatives} run-postinsts perl"
RDEPENDS_${PN}_class-native = ""

FILES_update-alternatives-dpkg = "${bindir}/update-alternatives ${localstatedir}/lib/dpkg/alternatives ${sysconfdir}/alternatives"
RPROVIDES_update-alternatives-dpkg = "update-alternatives"
RCONFLICTS_update-alternatives-dpkg = "update-alternatives"

PACKAGES += "${PN}-perl"
FILES_${PN}-perl = "${libdir}/perl"

BBCLASSEXTEND = "native"
