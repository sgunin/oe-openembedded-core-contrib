SUMMARY = "Package maintenance system from Debian"
SECTION = "base"
LICENSE = "GPLv2+"
DEPENDS = "perl"
DEPENDS_append_class-native = " bzip2-replacement-native"
PROVIDES = "virtual/update-alternatives"

SRC_URI_append_class-native = " file://0001-When-running-do_package_write_deb-we-have-trees-of-h.patch"

UPSTREAM_CHECK_URI = "${DEBIAN_MIRROR}/main/d/dpkg/"

inherit autotools gettext perlnative pkgconfig systemd

SYSTEMD_SERVICE_${PN} = "dpkg-configure.service"

export PERL = "${bindir}/perl"
PERL_class-native = "${STAGING_BINDIR_NATIVE}/perl-native/perl"
PERL_class-nativesdk = "${STAGING_BINDIR_NATIVE}/perl-native/perl"

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

EXTRA_OECONF += "--sbindir=${base_sbindir} --with-dpkg-deb-compressor=${DPKG_DEB_COMPRESSOR}"

EXTRA_OECONF += "TAR=tar"
EXTRA_OECONF += "DEB_HOST_ARCH=${DPKG_ARCH}"

do_configure_prepend_class-native() {
    sed -i -e 's|SYSCONFDIR|"/etc"|' ${S}/utils/update-alternatives.c
}

do_install_append_class-native() {
    sed -i -e 's|^#!${PERL}|#!/usr/bin/env nativeperl|' ${D}${bindir}/dpkg-*
}

do_install_append_class-target () {
    install -d ${D}${systemd_unitdir}/system
    install -m 0644 ${WORKDIR}/dpkg-configure.service ${D}${systemd_unitdir}/system
    sed -e 's,@BASE_BINDIR@,${base_bindir},g' \
        -e 's,@SYSCONFDIR@,${sysconfdir},g' \
        -e 's,@BINDIR@,${bindir},g' \
        -e 's,@SYSTEMD_UNITDIR@,${systemd_unitdir},g' \
        -i ${D}${systemd_unitdir}/system/dpkg-configure.service
}

PACKAGES =+ "dpkg-perl dselect libdpkg-perl update-alternatives-dpkg"

RDEPENDS_${PN} = "${VIRTUAL-RUNTIME_update-alternatives} run-postinsts perl"
RDEPENDS_${PN}_class-native = ""
RDEPENDS_${PN}_class-nativesdk = "nativesdk-${VIRTUAL-RUNTIME_update-alternatives} nativesdk-perl"
RSUGGESTS_${PN} = "apt"

RDEPENDS_dpkg-perl = "binutils libdpkg-perl make xz"
RRECOMMENDS_dpkg-perl = "gnupg gpgv"
RRECOMMENDS_dpkg-perl_class-nativesdk = ""

RDEPENDS_dselect = "dpkg"
RSUGGESTS_dselect = "perl"

RDEPENDS_libdpkg-perl = "dpkg libtimedate-perl perl"
RRECOMMENDS_libdpkg-perl = "xz"
RSUGGESTS_libdpkg-perl = "binutils gnupg gpgv"

RPROVIDES_update-alternatives-dpkg = "update-alternatives"
RCONFLICTS_update-alternatives-dpkg = "update-alternatives"

FILES_dpkg-perl = " \
    ${bindir}/dpkg-architecture \
    ${bindir}/dpkg-buildflags \
    ${bindir}/dpkg-buildpackage \
    ${bindir}/dpkg-checkbuilddeps \
    ${bindir}/dpkg-distaddfile \
    ${bindir}/dpkg-genchanges \
    ${bindir}/dpkg-gencontrol \
    ${bindir}/dpkg-gensymbols \
    ${bindir}/dpkg-mergechangelogs \
    ${bindir}/dpkg-name \
    ${bindir}/dpkg-parsechangelog \
    ${bindir}/dpkg-scanpackages \
    ${bindir}/dpkg-scansources \
    ${bindir}/dpkg-shlibdeps \
    ${bindir}/dpkg-source \
    ${bindir}/dpkg-vendor \
    ${datadir}/dpkg/*.mk \
"

FILES_dselect = "${bindir}/dselect"

FILES_libdpkg-perl = " \
    ${libdir}/dpkg/parsechangelog \
    ${libdir}/perl \
"

FILES_update-alternatives-dpkg = " \
    ${sysconfdir}/alternatives \
    ${bindir}/update-alternatives \
    ${localstatedir}/lib/dpkg/alternatives \
"

PKG_dpkg-dev = "libdpkg-dev"
PKG_dpkg-perl = "dpkg-dev"

BBCLASSEXTEND = "native nativesdk"
