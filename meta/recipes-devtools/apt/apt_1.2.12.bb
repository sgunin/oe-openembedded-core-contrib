DEPENDS = "curl db zlib"
RDEPENDS_${PN} = "dpkg bash debianutils"
require apt.inc

USE_NLS_class-native = "yes"

PACKAGES =+ "${PN}-utils"
FILES_${PN} += "${libdir}/dpkg ${systemd_system_unitdir}/apt-daily.service"
FILES_${PN}-utils = "${bindir}/apt-extracttemplates \
                     ${bindir}/apt-ftparchive \
                     ${bindir}/apt-sortpkgs"

PROGRAMS = " \
    apt apt-cache apt-cdrom apt-config apt-extracttemplates \
    apt-ftparchive apt-get apt-key apt-mark apt-sortpkgs \
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

PACKAGECONFIG ??= "lzma"
PACKAGECONFIG[lzma] = "ac_cv_lib_lzma_lzma_easy_encoder=yes,ac_cv_lib_lzma_lzma_easy_encoder=no,xz"
PACKAGECONFIG[bz2] = "ac_cv_lib_bz2_BZ2_bzopen=yes,ac_cv_lib_bz2_BZ2_bzopen=no,bzip2"
PACKAGECONFIG[lz4] = "ac_cv_lib_lz4_LZ4F_createCompressionContext=yes,ac_cv_lib_lz4_LZ4F_createCompressionContext=no,lz4"

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
