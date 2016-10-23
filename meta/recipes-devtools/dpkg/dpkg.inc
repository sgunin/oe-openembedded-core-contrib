SUMMARY = "Package maintenance system from Debian"
SECTION = "base"
LICENSE = "GPLv2+"
DEPENDS = "perl"
DEPENDS_append_class-native = " bzip2-replacement-native ${@bb.utils.contains('PACKAGECONFIG', 'update-alternatives', '', 'virtual/update-alternatives-native', d)}"
PROVIDES = "${@bb.utils.contains('PACKAGECONFIG', 'update-alternatives', 'virtual/update-alternatives', '', d)}"

UPSTREAM_CHECK_URI = "${DEBIAN_MIRROR}/main/d/dpkg/"

inherit autotools gettext perlnative pkgconfig systemd perl-version

SYSTEMD_SERVICE_${PN} = "dpkg-configure.service"

export PERL = "${bindir}/perl"
PERL_class-native = "${STAGING_BINDIR_NATIVE}/perl-native/perl"
PERL_class-nativesdk = "${STAGING_BINDIR_NATIVE}/perl-native/perl"

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

EXTRA_OECONF += "--sbindir=${base_sbindir} --with-dpkg-deb-compressor=${DPKG_DEB_COMPRESSOR}"

PACKAGECONFIG = "liblzma"
PACKAGECONFIG[liblzma] = "--with-liblzma,--without-liblzma, xz"

EXTRA_OECONF += "TAR=tar"
EXTRA_OECONF_append_class-target = " DEB_HOST_ARCH=${DPKG_ARCH}"

do_configure_prepend_class-native() {
    sed -i -e 's|SYSCONFDIR|"${sysconfdir_native}"|' ${S}/utils/update-alternatives.c
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

PACKAGES =+ "dpkg-perl dselect libdpkg-perl start-stop-daemon update-alternatives-dpkg"

RDEPENDS_${PN} = "${VIRTUAL-RUNTIME_update-alternatives}"
RDEPENDS_${PN}_class-native = ""
RDEPENDS_${PN}_class-nativesdk = "nativesdk-${VIRTUAL-RUNTIME_update-alternatives} nativesdk-perl"
RRECOMMENDS_${PN} = "start-stop-daemon"
RRECOMMENDS_${PN}_class-native = ""
RSUGGESTS_${PN} = "apt"

RDEPENDS_dpkg-perl = "binutils libdpkg-perl make xz"
RRECOMMENDS_dpkg-perl = "gnupg"
RRECOMMENDS_dpkg-perl_class-nativesdk = ""

RDEPENDS_dselect = "dpkg"
RSUGGESTS_dselect = "perl"

RDEPENDS_libdpkg-perl = "dpkg libtimedate-perl perl"
RRECOMMENDS_libdpkg-perl = "xz"
RSUGGESTS_libdpkg-perl = "binutils gnupg"

RPROVIDES_update-alternatives-dpkg = "update-alternatives"
RCONFLICTS_update-alternatives-dpkg = "update-alternatives"

RDEPENDS_libdpkg-perl += "perl-module-carp perl-module-constant \
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
    ${libdir}/perl/${@get_perl_version(d)} \
"

FILES_start-stop-daemon = "${base_sbindir}/start-stop-daemon"

FILES_update-alternatives-dpkg = " \
    ${sysconfdir}/alternatives \
    ${bindir}/update-alternatives \
    ${localstatedir}/lib/dpkg/alternatives \
"

PKG_dpkg-dev = "libdpkg-dev"
PKG_dpkg-perl = "dpkg-dev"

BBCLASSEXTEND = "native nativesdk"
