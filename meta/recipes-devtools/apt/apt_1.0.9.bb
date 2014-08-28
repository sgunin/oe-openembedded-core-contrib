SUMMARY = "commandline package manager"
SECTION = "base"
LICENSE = "GPLv2+"
LIC_FILES_CHKSUM = "file://COPYING.GPL;md5=0636e73ff0215e8d672dc4c32c317bb3"
DEPENDS = "curl db"

SRC_URI = "${DEBIAN_MIRROR}/main/a/apt/apt_${PV}.tar.xz \
           file://use-host.patch \
           file://makerace.patch \
           file://no-nls-dpkg.patch \
           file://truncate-filename.patch \
           file://disable-configure-in-makefile.patch \
           file://db_linking_hack.patch \
           file://Fix-regression-for-file-uris-from-CVE-2014-0487.patch \
           file://disable-tests-and-drop-dependency-on-gtest.patch \
           file://apt-opkg-compatibility-shim-to-ease-migration-from-o.patch \
           file://apt.conf.in"
SRC_URI[md5sum] = "14d68e7bc6a5f3cef28ca1edf744cdbe"
SRC_URI[sha256sum] = "4e6f25464a38e94961e107ebd1afb72dbb865d096504aa7194f55c755706c071"

S = "${WORKDIR}/apt-1.0.8"

inherit autotools gettext update-alternatives

EXTRA_AUTORECONF = "--exclude=autopoint,autoheader"
EXTRA_OECONF = "--disable-rpath --without-getconf"
CACHED_CONFIGUREVARS = " \
    ac_cv_path_DOT= \
    ac_cv_path_DOXYGEN= \
    ac_cv_path_PO4A= \
    ac_cv_path_W3M= \
    ac_cv_path_XSLTPROC= \
"
USE_NLS_class-native = "yes"

do_configure_prepend() {
    for f in config.guess config.sub; do
        rm -f ${S}/buildlib/$f
    done
}

do_install() {
    install -d ${D}${sysconfdir}/apt/apt.conf.d
    install -d ${D}${sysconfdir}/apt/preferences.d
    install -d ${D}${sysconfdir}/apt/sources.list.d
    install -d ${D}${sysconfdir}/apt/trusted.gpg.d

    install -d ${D}${bindir}
    for f in apt apt-cache apt-cdrom apt-config apt-extracttemplates apt-ftparchive apt-get apt-key apt-mark apt-opkg apt-sortpkgs; do
        install -m 0755 bin/$f ${D}${bindir}
    done

    install -d ${D}${includedir}/apt-pkg
    install -m 0644 include/apt-pkg/*.h ${D}${includedir}/apt-pkg

    install -d ${D}${libdir}
    for f in inst pkg private; do
        oe_libinstall -so -C bin libapt-$f ${D}${libdir}
    done

    install -d ${D}${libdir}/apt
    install -m 0755 bin/apt-helper ${D}${libdir}/apt

    install -d ${D}${libdir}/apt/methods
    install -m 0755 bin/methods/* ${D}${libdir}/apt/methods

    install -d ${D}${libdir}/dpkg/methods/apt
    for f in desc.apt names; do
        install -m 0644 ${S}/dselect/$f ${D}${libdir}/dpkg/methods/apt
    done
    for f in install setup update; do
        install -m 0755 ${S}/dselect/$f ${D}${libdir}/dpkg/methods/apt
    done

    install -d ${D}${docdir}/apt/examples
    install -m 0644 ${S}/doc/examples/* ${D}${docdir}/apt/examples

    for i in 1 5 8; do
        install -d ${D}${mandir}/man${i}
        install -m 0644 ${S}/doc/en/*.${i} ${D}${mandir}/man${i}
    done

    install -d ${D}${localstatedir}/cache/apt/archives/partial
    install -d ${D}${localstatedir}/lib/apt/lists/partial
    install -d ${D}${localstatedir}/lib/apt/mirrors/partial
    install -d ${D}${localstatedir}/lib/apt/periodic
    install -d ${D}${localstatedir}/log/apt
}

do_install_append_class-native() {
    sed -e "s,@STAGING_DIR_NATIVE@,${STAGING_DIR_NATIVE},g" \
        -e "s,@STAGING_BINDIR_NATIVE@,${STAGING_BINDIR_NATIVE},g" \
        -e "s,@STAGING_LIBDIR@,${STAGING_LIBDIR},g" \
        < ${WORKDIR}/apt.conf.in > ${D}${sysconfdir}/apt/apt.conf.sample
}

do_install_append_class-target() {
    # Write the correct apt-architecture to apt.conf
    echo 'APT::Architecture "${DPKG_ARCH}";' > ${D}${sysconfdir}/apt/apt.conf
}

PACKAGES =+ "apt-opkg apt-transport-https apt-utils libapt-inst libapt-pkg"

RDEPENDS_${PN} = "dpkg"

RRECOMMENDS_${PN} = "gnupg"
RRECOMMENDS_${PN}_class-native = ""

RPROVIDES_apt-opkg = "opkg"

FILES_${PN} += "${libdir}/dpkg"
FILES_${PN}-dbg += "${libdir}/apt/methods/.debug"
FILES_apt-opkg = "${bindir}/apt-opkg"
FILES_apt-transport-https = "${libdir}/apt/methods/https"
FILES_apt-utils = "${bindir}/apt-extracttemplates \
                   ${bindir}/apt-ftparchive \
                   ${bindir}/apt-sortpkgs"
FILES_libapt-inst = "${libdir}/libapt-inst${SOLIBS}"
FILES_libapt-pkg = "${libdir}/libapt-pkg${SOLIBS}"

ALTERNATIVE_apt-opkg = "opkg"
ALTERNATIVE_TARGET_apt-opkg[opkg] = "${bindir}/apt-opkg"

BBCLASSEXTEND = "native"
