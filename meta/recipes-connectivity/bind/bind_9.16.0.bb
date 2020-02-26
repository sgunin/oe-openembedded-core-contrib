SUMMARY = "ISC Internet Domain Name Server"
HOMEPAGE = "http://www.isc.org/sw/bind/"
SECTION = "console/network"

LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://COPYRIGHT;md5=188b8d0644bd6835df43b84e3f180be1"

DEPENDS = "openssl libcap zlib libuv"

SRC_URI = "https://ftp.isc.org/isc/bind9/${PV}/${BPN}-${PV}.tar.xz \
           file://conf.patch \
           file://named.service \
           file://bind9 \
           file://generate-rndc-key.sh \
           file://make-etc-initd-bind-stop-work.patch \
           file://init.d-add-support-for-read-only-rootfs.patch \
           file://bind-ensure-searching-for-json-headers-searches-sysr.patch \
           file://0001-named-lwresd-V-and-start-log-hide-build-options.patch \
           file://0001-avoid-start-failure-with-bind-user.patch \
           "

SRC_URI[md5sum] = "f973848bf32797c5f546de7d92a6fd07"
SRC_URI[sha256sum] = "af4bd9bdaeb1aa7399429972f3a8aa01dd6886b7ae046d703ab8da45330f2e28"
SRC_URI[sha1sum] = "01678973598db55dfb03cd12a004d3d75a8ab2bf"
SRC_URI[sha384sum] = "29df1a4a9d2a460ff970ea3d9abb47ff1204c762fa5c23da0627b0eeb847560f706d333796f61c5aeba09b8333532359"
SRC_URI[sha512sum] = "6f836fe4cb12d3e2e3a871f69e5b249b2bae6ae1dc542395c79cc974ca95aa595e963b6716a38f6c9d5cb3c54ec606fcd932412a6fbe596651123ac1379dd010"

UPSTREAM_CHECK_URI = "https://ftp.isc.org/isc/bind9/"
# stay at 9.16 follow the ESV versions divisible by 4
UPSTREAM_CHECK_REGEX = "(?P<pver>9.(11|16|20|24|28)(\.\d+)+(-P\d+)*)/"

inherit autotools update-rc.d systemd useradd pkgconfig multilib_script multilib_header

MULTILIB_SCRIPTS = "${PN}:${bindir}/bind9-config ${PN}:${bindir}/isc-config.sh"

# PACKAGECONFIGs readline and libedit should NOT be set at same time
PACKAGECONFIG ?= "readline"
PACKAGECONFIG[httpstats] = "--with-libxml2=${STAGING_DIR_HOST}${prefix},--without-libxml2,libxml2"
PACKAGECONFIG[readline] = "--with-readline=-lreadline,,readline"
PACKAGECONFIG[libedit] = "--with-readline=-ledit,,libedit"
PACKAGECONFIG[python3] = "--with-python=yes --with-python-install-dir=${PYTHON_SITEPACKAGES_DIR} , --without-python, python3-ply-native,"

EXTRA_OECONF = " --with-libtool --disable-devpoll --enable-epoll \
                 --with-gssapi=no --with-lmdb=no --with-zlib \
                 --sysconfdir=${sysconfdir}/bind \
                 --with-openssl=${STAGING_DIR_HOST}${prefix} \
               "
LDFLAGS_append = " -lz"

inherit ${@bb.utils.contains('PACKAGECONFIG', 'python3', 'python3native distutils3-base', '', d)}

# dhcp needs .la so keep them
REMOVE_LIBTOOL_LA = "0"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM_${PN} = "--system --home ${localstatedir}/cache/bind --no-create-home \
                       --user-group bind"

INITSCRIPT_NAME = "bind"
INITSCRIPT_PARAMS = "defaults"

SYSTEMD_SERVICE_${PN} = "named.service"

do_install_append() {

	rmdir "${D}${localstatedir}/run"
	rmdir --ignore-fail-on-non-empty "${D}${localstatedir}"
	install -d -o bind "${D}${localstatedir}/cache/bind"
	install -d "${D}${sysconfdir}/bind"
	install -d "${D}${sysconfdir}/init.d"
	install -m 644 ${S}/conf/* "${D}${sysconfdir}/bind/"
	install -m 755 "${S}/init.d" "${D}${sysconfdir}/init.d/bind"
        if ${@bb.utils.contains('PACKAGECONFIG', 'python3', 'true', 'false', d)}; then
		sed -i -e '1s,#!.*python3,#! /usr/bin/python3,' \
		${D}${sbindir}/dnssec-coverage \
		${D}${sbindir}/dnssec-checkds \
		${D}${sbindir}/dnssec-keymgr
	fi

	# Install systemd related files
	install -d ${D}${sbindir}
	install -m 755 ${WORKDIR}/generate-rndc-key.sh ${D}${sbindir}
	install -d ${D}${systemd_unitdir}/system
	install -m 0644 ${WORKDIR}/named.service ${D}${systemd_unitdir}/system
	sed -i -e 's,@BASE_BINDIR@,${base_bindir},g' \
	       -e 's,@SBINDIR@,${sbindir},g' \
	       ${D}${systemd_unitdir}/system/named.service

	install -d ${D}${sysconfdir}/default
	install -m 0644 ${WORKDIR}/bind9 ${D}${sysconfdir}/default

	if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
		install -d ${D}${sysconfdir}/tmpfiles.d
		echo "d /run/named 0755 bind bind - -" > ${D}${sysconfdir}/tmpfiles.d/bind.conf
	fi

    oe_multilib_header isc/platform.h
}

CONFFILES_${PN} = " \
	${sysconfdir}/bind/named.conf \
	${sysconfdir}/bind/named.conf.local \
	${sysconfdir}/bind/named.conf.options \
	${sysconfdir}/bind/db.0 \
	${sysconfdir}/bind/db.127 \
	${sysconfdir}/bind/db.empty \
	${sysconfdir}/bind/db.local \
	${sysconfdir}/bind/db.root \
	"

ALTERNATIVE_${PN}-utils = "nslookup"
ALTERNATIVE_LINK_NAME[nslookup] = "${bindir}/nslookup"
ALTERNATIVE_PRIORITY = "100"

PACKAGE_BEFORE_PN += "${PN}-utils"
FILES_${PN}-utils = "${bindir}/host ${bindir}/dig ${bindir}/mdig ${bindir}/nslookup ${bindir}/nsupdate"
FILES_${PN}-dev += "${bindir}/isc-config.h"
FILES_${PN} += "${sbindir}/generate-rndc-key.sh"

PACKAGE_BEFORE_PN += "${PN}-libs"
FILES_${PN}-libs = "${libdir}/*.so* ${libdir}/named/*.so*"
FILES_${PN}-staticdev += "${libdir}/*.la"

PACKAGE_BEFORE_PN += "${@bb.utils.contains('PACKAGECONFIG', 'python3', 'python3-bind', '', d)}"
FILES_python3-bind = "${sbindir}/dnssec-coverage ${sbindir}/dnssec-checkds \
                ${sbindir}/dnssec-keymgr ${PYTHON_SITEPACKAGES_DIR}"

RDEPENDS_${PN}-dev = ""
RDEPENDS_python3-bind = "python3-core python3-ply"
