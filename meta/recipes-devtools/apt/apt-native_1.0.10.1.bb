require apt.inc
inherit native

DEPENDS += "dpkg-native gettext-native db-native curl-native xz-native"
PACKAGES = ""
USE_NLS = "yes"

SRC_URI += "file://0001-remove-Wsuggest-attribute-from-CFLAGS.patch \
            file://0001-fix-the-gcc-version-check.patch \
            file://apt.conf.in \
"

do_install() {
	install -d ${D}${bindir}
	install -m 0755 bin/apt-cdrom ${D}${bindir}/
	install -m 0755 bin/apt-get ${D}${bindir}/
	install -m 0755 bin/apt-config ${D}${bindir}/
	install -m 0755 bin/apt-cache ${D}${bindir}/
	install -m 0755 bin/apt-sortpkgs ${D}${bindir}/
	install -m 0755 bin/apt-extracttemplates ${D}${bindir}/
	install -m 0755 bin/apt-ftparchive ${D}${bindir}/

	oe_libinstall -so -C bin libapt-private ${D}${libdir}/

	oe_libinstall -so -C bin libapt-pkg$GLIBC_VER$LIBSTDCPP_VER ${D}${libdir}/
	oe_libinstall -so -C bin libapt-inst$GLIBC_VER$LIBSTDCPP_VER ${D}${libdir}/

	install -d ${D}${libdir}/apt/methods
	install -m 0755 bin/methods/* ${D}${libdir}/apt/methods/

	install -d ${D}${libdir}/dpkg/methods/apt
	install -m 0644 ${S}/dselect/desc.apt ${D}${libdir}/dpkg/methods/apt/ 
	install -m 0644 ${S}/dselect/names ${D}${libdir}/dpkg/methods/apt/ 
	install -m 0755 ${S}/dselect/install ${D}${libdir}/dpkg/methods/apt/ 
	install -m 0755 ${S}/dselect/setup ${D}${libdir}/dpkg/methods/apt/ 
	install -m 0755 ${S}/dselect/update ${D}${libdir}/dpkg/methods/apt/ 

	install -d ${D}${sysconfdir}/apt
	install -d ${D}${sysconfdir}/apt/apt.conf.d
	install -d ${D}${sysconfdir}/apt/preferences.d
	install -d ${D}${localstatedir}/lib/apt/lists/partial
	install -d ${D}${localstatedir}/cache/apt/archives/partial

	install -d ${D}${localstatedir}/log/apt/

	sed -e "s,@STAGING_DIR_NATIVE@,${STAGING_DIR_NATIVE},g" \
	    -e "s,@STAGING_BINDIR_NATIVE@,${STAGING_BINDIR_NATIVE},g" \
	    -e "s,@STAGING_LIBDIR@,${STAGING_LIBDIR},g" \
	    < ${WORKDIR}/apt.conf.in > ${D}${sysconfdir}/apt/apt.conf.sample
}
