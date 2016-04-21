SUMMARY = "Advanced front-end for dpkg"
SECTION = "base"
LICENSE = "GPLv2.0+"
DEPENDS = "curl db zlib"
RDEPENDS_${PN} = "dpkg bash debianutils"

SRC_URI = "http://snapshot.debian.org/archive/debian/20150805T094928Z/pool/main/a/${BPN}/${BPN}_${PV}.tar.xz \
           file://use-host.patch \
           file://makerace.patch \
           file://no-nls-dpkg.patch \
           file://fix-gcc-4.6-null-not-defined.patch \
           file://truncate-filename.patch \
           file://nodoc.patch \
           file://disable-configure-in-makefile.patch \
           file://disable-test.patch \
           file://0001-environment.mak-musl-based-systems-can-generate-shar.patch \
           file://0001-remove-Wsuggest-attribute-from-CFLAGS.patch \
           file://0001-fix-the-gcc-version-check.patch \
           file://apt.conf.in \
           "
SRC_URI[md5sum] = "6505c4297b338adb2087ce87bbc4a276"
SRC_URI[sha256sum] = "3fb1de9598363c416591d49e3c285458e095b035e6c06d5b944a54e15fc9b543"
LIC_FILES_CHKSUM = "file://COPYING.GPL;md5=0636e73ff0215e8d672dc4c32c317bb3"

# the package is taken from snapshots.debian.org; that source is static and goes stale
# so we check the latest upstream from a directory that does get updated
UPSTREAM_CHECK_URI = "${DEBIAN_MIRROR}/main/a/apt/"

inherit autotools gettext

EXTRA_AUTORECONF = "--exclude=autopoint,autoheader"

do_configure_prepend() {
    rm -rf ${S}/buildlib/config.sub
    rm -rf ${S}/buildlib/config.guess
}

USE_NLS_class-native = "yes"

PACKAGES =+ "${PN}-utils"
FILES_${PN} += "${libdir}/dpkg"
FILES_${PN}-utils = "${bindir}/apt-extracttemplates \
                     ${bindir}/apt-ftparchive \
                     ${bindir}/apt-sortpkgs"

PROGRAMS = " \
    apt apt-cache apt-cdrom apt-config apt-extracttemplates \
    apt-ftparchive apt-get apt-key apt-mark apt-sortpkgs \
"

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
}

PACKAGECONFIG ??= "lzma"
PACKAGECONFIG[lzma] = "ac_cv_lib_lzma_lzma_easy_encoder=yes,ac_cv_lib_lzma_lzma_easy_encoder=no,xz"
PACKAGECONFIG[bz2] = "ac_cv_lib_bz2_BZ2_bzopen=yes,ac_cv_lib_bz2_BZ2_bzopen=no,bzip2"

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

BBCLASSEXTEND = "native"
