SUMMARY = "Advanced front-end for dpkg"
SECTION = "base"
LICENSE = "GPLv2.0+"
LIC_FILES_CHKSUM = "file://COPYING.GPL;md5=0636e73ff0215e8d672dc4c32c317bb3"
DEPENDS = "curl db zlib"

SRC_URI = "http://snapshot.debian.org/archive/debian/20160526T162943Z/pool/main/a/${BPN}/${BPN}_${PV}.tar.xz \
           file://use-host.patch \
           file://makerace.patch \
           file://no-nls-dpkg.patch \
           file://fix-gcc-4.6-null-not-defined.patch \
           file://truncate-filename.patch \
           file://nodoc.patch \
           file://disable-configure-in-makefile.patch \
           file://disable-test.patch \
           file://0001-environment.mak-musl-based-systems-can-generate-shar.patch \
           file://0001-apt-1.2.12-Fix-musl-build.patch \
           file://0001-remove-Wsuggest-attribute-from-CFLAGS.patch \
           file://0001-fix-the-gcc-version-check.patch \
           file://0001-apt-opkg-compatibility-shim-to-ease-migration-from-o.patch \
           file://apt.conf.in \
           "
SRC_URI[md5sum] = "80f6f0ef110a45a7e5af8a9d233fb0e7"
SRC_URI[sha256sum] = "e820d27cba73476df4abcff27dadd1b5847474bfe85f7e9202a9a07526973ea6"

# the package is taken from snapshots.debian.org; that source is static and goes stale
# so we check the latest upstream from a directory that does get updated
UPSTREAM_CHECK_URI = "${DEBIAN_MIRROR}/main/a/apt/"

inherit autotools gettext update-alternatives useradd

AUTOTOOLS_AUXDIR = "${S}/buildlib"

EXTRA_AUTORECONF = "--exclude=autopoint,autoheader"
EXTRA_OECONF = "--disable-rpath"

PACKAGECONFIG ??= "lzma"
PACKAGECONFIG[lzma] = "ac_cv_lib_lzma_lzma_easy_encoder=yes,ac_cv_lib_lzma_lzma_easy_encoder=no,xz"
PACKAGECONFIG[bz2] = "ac_cv_lib_bz2_BZ2_bzopen=yes,ac_cv_lib_bz2_BZ2_bzopen=no,bzip2"
PACKAGECONFIG[lz4] = "ac_cv_lib_lz4_LZ4F_createCompressionContext=yes,ac_cv_lib_lz4_LZ4F_createCompressionContext=no,lz4"
PACKAGECONFIG[opkg] = ",,,"

USE_NLS_class-native = "yes"

do_configure_prepend() {
    rm -rf ${S}/buildlib/config.sub
    rm -rf ${S}/buildlib/config.guess
}

USERADD_PACKAGES = "${PN}"
USERADD_PARAM_${PN} = "--system --no-create-home --home-dir /nonexistent --shell /bin/false --user-group _apt"

PROGRAMS = " \
    apt apt-cache apt-cdrom apt-config apt-extracttemplates \
    apt-ftparchive apt-get apt-key apt-mark apt-sortpkgs \
    ${@bb.utils.contains('PACKAGECONFIG', 'opkg', 'apt-opkg', '', d)} \
"

inherit systemd

SYSTEMD_SERVICE_${PN} = "apt-daily.timer"

do_install () {
	install -d ${D}${bindir}
	for f in ${PROGRAMS}; do
		install -m 0755 bin/$f ${D}${bindir}
	done

	install -d ${D}${docdir}/apt/examples
	install -m 0644 ${S}/doc/examples/* ${D}${docdir}/apt/examples

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

	for d in apt.conf.d preferences.d sources.list.d trusted.gpg.d; do
		install -d ${D}${sysconfdir}/apt/$d
	done

	install -d ${D}${localstatedir}/cache/apt/archives/partial
	install -d ${D}${localstatedir}/lib/apt/lists/partial
	install -d ${D}${localstatedir}/lib/apt/mirrors/partial
	install -d ${D}${localstatedir}/lib/apt/periodic
	install -d ${D}${localstatedir}/log/apt

	install -d ${D}${systemd_unitdir}/system/
	install -m 0644 ${S}/debian/apt.systemd.daily ${D}${libdir}/apt/
	install -m 0644 ${S}/debian/apt-daily.service ${D}${systemd_unitdir}/system/
	sed -i 's#/usr/lib/apt/#${libdir}/apt/#g' ${D}${systemd_unitdir}/system/apt-daily.service
	install -m 0644 ${S}/debian/apt-daily.timer ${D}${systemd_unitdir}/system/
	install -d ${D}${sysconfdir}/cron.daily/
	install -m 0755 ${S}/debian/apt.apt-compat.cron.daily ${D}${sysconfdir}/cron.daily/
	sed -i 's#/usr/lib/apt/#${libdir}/apt/#g' ${D}${sysconfdir}/cron.daily/apt.apt-compat.cron.daily
}

do_install_append_class-native() {
    sed -e "s,@STAGING_DIR_NATIVE@,${STAGING_DIR_NATIVE},g" \
        -e "s,@STAGING_BINDIR_NATIVE@,${STAGING_BINDIR_NATIVE},g" \
        -e "s,@STAGING_LIBDIR@,${STAGING_LIBDIR},g" \
        < ${WORKDIR}/apt.conf.in > ${D}${sysconfdir}/apt/apt.conf.sample
}

do_install_append_class-target() {
    #Write the correct apt-architecture to apt.conf
    echo 'APT::Architecture "${DPKG_ARCH}";' > ${D}${sysconfdir}/apt/apt.conf
}

PACKAGES =+ "${PN}-dselect ${PN}-opkg ${PN}-transport-https ${PN}-utils lib${PN}-inst lib${PN}-pkg"

RDEPENDS_${PN} = "dpkg debianutils"
RDEPENDS_${PN}-dselect = "bash perl"

RRECOMMENDS_${PN} = "gnupg"
RRECOMMENDS_${PN}_class-native = ""

RPROVIDES_${PN}-opkg = "opkg"

FILES_${PN} += "${libdir}/dpkg ${systemd_system_unitdir}/apt-daily.service"
FILES_${PN}-dselect = "${libdir}/dpkg/methods/apt"
FILES_${PN}-opkg = "${bindir}/apt-opkg"
FILES_${PN}-transport-https = "${libdir}/apt/methods/https"
FILES_${PN}-utils = "${bindir}/apt-extracttemplates \
                     ${bindir}/apt-ftparchive \
                     ${bindir}/apt-sortpkgs"
FILES_lib${PN}-inst = "${libdir}/libapt-inst${SOLIBS}"
FILES_lib${PN}-pkg = "${libdir}/libapt-pkg${SOLIBS}"

ALTERNATIVE_${PN}-opkg = "${@bb.utils.contains('PACKAGECONFIG', 'opkg', 'opkg', '', d)}"
ALTERNATIVE_TARGET[opkg] = "${bindir}/apt-opkg"

BBCLASSEXTEND = "native"
