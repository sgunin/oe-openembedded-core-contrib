DEPENDS = "curl db zlib"
RDEPENDS_${PN} = "dpkg bash debianutils"
require apt.inc

PACKAGES += "${PN}-utils"
FILES_${PN} = "${bindir}/apt-cdrom ${bindir}/apt-get \
	       ${bindir}/apt-config ${bindir}/apt-cache \
	       ${libdir}/apt ${libdir}/libapt*.so.* \
	       ${localstatedir} ${sysconfdir} \
	       ${libdir}/dpkg"
FILES_${PN}-utils = "${bindir}/apt-sortpkgs ${bindir}/apt-extracttemplates"
FILES_${PN}-dev = "${libdir}/libapt*.so ${includedir}"

do_install () {
	set -x
	install -d ${D}${bindir}
	install -m 0755 bin/apt-key ${D}${bindir}/
	install -m 0755 bin/apt-cdrom ${D}${bindir}/
	install -m 0755 bin/apt-get ${D}${bindir}/
	install -m 0755 bin/apt-config ${D}${bindir}/
	install -m 0755 bin/apt-cache ${D}${bindir}/

	install -m 0755 bin/apt-sortpkgs ${D}${bindir}/
	install -m 0755 bin/apt-extracttemplates ${D}${bindir}/

	oe_libinstall -so -C bin libapt-pkg ${D}${libdir}
	oe_libinstall -so -C bin libapt-inst ${D}${libdir}

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
	install -d ${D}${sysconfdir}/apt/sources.list.d
	install -d ${D}${sysconfdir}/apt/preferences.d
	install -d ${D}${localstatedir}/lib/apt/lists/partial
	install -d ${D}${localstatedir}/cache/apt/archives/partial
	install -d ${D}${docdir}/apt/examples
	install -m 0644 ${S}/doc/examples/* ${D}${docdir}/apt/examples/

	install -d ${D}${includedir}/apt-pkg/
	install -m 0644 include/apt-pkg/*.h ${D}${includedir}/apt-pkg/
}

PACKAGECONFIG ??= "lzma"
PACKAGECONFIG[lzma] = "ac_cv_lib_lzma_lzma_easy_encoder=yes,ac_cv_lib_lzma_lzma_easy_encoder=no,xz"
PACKAGECONFIG[bz2] = "ac_cv_lib_bz2_BZ2_bzopen=yes,ac_cv_lib_bz2_BZ2_bzopen=no,bzip2"

FILES_${PN} += "${bindir}/apt-key"

do_install_append() {
    #Write the correct apt-architecture to apt.conf
    APT_CONF=${D}/etc/apt/apt.conf
    echo 'APT::Architecture "${DPKG_ARCH}";' > ${APT_CONF}
    oe_libinstall -so -C bin libapt-private ${D}${libdir}/
}
