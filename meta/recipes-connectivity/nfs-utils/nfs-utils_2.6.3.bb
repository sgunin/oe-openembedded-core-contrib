SUMMARY = "userspace utilities for kernel nfs"
DESCRIPTION = "The nfs-utils package provides a daemon for the kernel \
NFS server and related tools."
HOMEPAGE = "http://nfs.sourceforge.net/"
SECTION = "console/network"

LICENSE = "MIT & GPL-2.0-or-later & BSD-3-Clause"
LIC_FILES_CHKSUM = "file://COPYING;md5=95f3a93a5c3c7888de623b46ea085a84"

# util-linux for libblkid
DEPENDS = "libcap libevent util-linux sqlite3 libtirpc"
RDEPENDS:${PN} = "${PN}-client"
RRECOMMENDS:${PN} = "kernel-module-nfsd"

inherit useradd

USERADD_PACKAGES = "${PN}-client"
USERADD_PARAM:${PN}-client = "--system  --home-dir /var/lib/nfs \
			      --shell /bin/false --user-group rpcuser"

SRC_URI = "${KERNELORG_MIRROR}/linux/utils/nfs-utils/${PV}/nfs-utils-${PV}.tar.xz \
           file://nfsserver \
           file://nfscommon \
           file://nfs-utils.conf \
           file://nfs-utils-debianize-start-statd.patch \
           file://bugfix-adjust-statd-service-name.patch \
           file://0001-Makefile.am-fix-undefined-function-for-libnsm.a.patch \
           file://clang-warnings.patch \
           file://0001-configure.ac-libevent-and-libsqlite3-checked-when-nf.patch \
           file://0001-locktest-Makefile.am-Do-not-use-build-flags.patch \
           file://0001-tools-locktest-Use-intmax_t-to-print-off_t.patch \
           file://0001-gssd-use-printf-format-specifiers.patch \
           file://0002-Use-nogroup-for-nobody-group.patch \
           file://0003-find-OE-provided-Kerberos.patch \
           "
SRC_URI[sha256sum] = "38d89e853a71d3c560ff026af3d969d75e24f782ff68324e76261fe0344459e1"

# Only kernel-module-nfsd is required here (but can be built-in)  - the nfsd module will
# pull in the remainder of the dependencies.

INITSCRIPT_PACKAGES = "${PN} ${PN}-client"
INITSCRIPT_NAME = "nfsserver"
INITSCRIPT_PARAMS = "defaults"
INITSCRIPT_NAME:${PN}-client = "nfscommon"
INITSCRIPT_PARAMS:${PN}-client = "defaults 19 21"

inherit autotools-brokensep update-rc.d systemd pkgconfig

SYSTEMD_PACKAGES = "${PN} ${PN}-client"
SYSTEMD_SERVICE:${PN} = "nfs-server.service nfs-mountd.service"
SYSTEMD_SERVICE:${PN}-client = "nfs-client.target"

# --enable-uuid is need for cross-compiling
EXTRA_OECONF = "--with-statduser=rpcuser \
                --enable-mountconfig \
                --enable-libmount-mount \
                --enable-uuid \
                --disable-sbin-override \
                --with-statdpath=/var/lib/nfs/statd \
                --with-pluginpath=${libdir}/libnfsidmap \
                --with-rpcgen=${HOSTTOOLS_DIR}/rpcgen \
               "

LDFLAGS:append = " -lsqlite3 -levent"

PACKAGECONFIG ??= "tcp-wrappers nfsv4 nfsv41 \
    ${@bb.utils.filter('DISTRO_FEATURES', 'ipv6 systemd', d)} \
"

PACKAGECONFIG:remove:libc-musl = "tcp-wrappers"
PACKAGECONFIG[gssapi] = "--with-krb5=${STAGING_EXECPREFIXDIR} --enable-gss --enable-svcgss,--disable-gss --disable-svcgss,krb5"
PACKAGECONFIG[tcp-wrappers] = "--with-tcp-wrappers,--without-tcp-wrappers,tcp-wrappers"
PACKAGECONFIG[ipv6] = "--enable-ipv6,--disable-ipv6,"
# libdevmapper is available in meta-oe
PACKAGECONFIG[nfsv41] = "--enable-nfsv41,--disable-nfsv41,libdevmapper,libdevmapper"
# keyutils is available in meta-oe
PACKAGECONFIG[nfsv4] = "--enable-nfsv4,--disable-nfsv4,keyutils,python3-core"
PACKAGECONFIG[systemd] = "--with-systemd=${systemd_unitdir}/system,--without-systemd"

PACKAGES =+ "${PN}-client ${PN}-mount ${PN}-stats ${PN}-rpcctl"

CONFFILES:${PN}-client += "${localstatedir}/lib/nfs/etab \
			   ${localstatedir}/lib/nfs/rmtab \
			   ${localstatedir}/lib/nfs/xtab \
			   ${localstatedir}/lib/nfs/statd/state \
			   ${sysconfdir}/nfs.conf \
			   ${sysconfdir}/idmapd.conf \
			   ${sysconfdir}/nfsmount.conf"

FILES:${PN}-client = "${sbindir}/*statd \
		      ${libdir}/libnfsidmap/*.so \
		      ${libdir}/libnfsidmap.so.* \
		      ${libexecdir}/nfsrahead \
		      ${sbindir}/rpc.idmapd ${sbindir}/sm-notify \
		      ${sbindir}/showmount ${sbindir}/nfsstat \
		      ${sbindir}/nfsidmap ${sbindir}/nfsconf \
		      ${localstatedir}/lib/nfs \
		      ${sysconfdir}/idmapd.conf \
		      ${sysconfdir}/nfs.conf \
		      ${sysconfdir}/nfsmount.conf \
		      ${sysconfdir}/init.d/nfscommon \
		      ${base_prefix}/lib/udev \
		      ${systemd_unitdir}/system-generators/rpc-pipefs-generator \
		      ${systemd_system_unitdir}/var-lib-nfs-rpc_pipefs.mount \
		      ${systemd_system_unitdir}/rpc_pipefs.target \
		      ${systemd_system_unitdir}/nfs-statd.service \
		      ${systemd_system_unitdir}/nfs-idmapd.service \
		      ${systemd_system_unitdir}/rpc-statd.service \
		      ${systemd_system_unitdir}/rpc-statd-notify.service \
		      ${systemd_system_unitdir}/nfs-client.target"
RDEPENDS:${PN}-client = "${PN}-mount rpcbind"

FILES:${PN}-mount = "${base_sbindir}/*mount.nfs*"

FILES:${PN}-stats = "${sbindir}/mountstats ${sbindir}/nfsiostat ${sbindir}/nfsdclnts"
RDEPENDS:${PN}-stats = "python3-core"

FILES:${PN}-rpcctl = "${sbindir}/rpcctl"
RDEPENDS:${PN}-rpcctl = "python3-core"

FILES:${PN}-staticdev += "${libdir}/libnfsidmap/*.a"

FILES:${PN} += "${systemd_unitdir} ${libdir}/libnfsidmap/ ${nonarch_libdir}/modprobe.d"

do_configure:prepend() {
    sed -i -e 's,unit_dir = /usr/lib/systemd/system,unit_dir = ${systemd_unitdir}/system,g' \
        -e 's,generator_dir = /usr/lib/systemd/system-generators,generator_dir = ${systemd_unitdir}/system-generators,g' \
        ${S}/systemd/Makefile.am
}

# Make clean needed because the package comes with
# precompiled 64-bit objects that break the build
do_compile:prepend() {
	make clean
}

# Works on systemd only
HIGH_RLIMIT_NOFILE ??= "4096"

do_install:append () {
	install -d ${D}${sysconfdir}/init.d
	install -m 0755 ${WORKDIR}/nfsserver ${D}${sysconfdir}/init.d/nfsserver
	install -m 0755 ${WORKDIR}/nfscommon ${D}${sysconfdir}/init.d/nfscommon

	install -m 0644 ${S}/utils/mount/nfsmount.conf ${D}${sysconfdir}
	install -m 0644 ${S}/support/nfsidmap/idmapd.conf ${D}${sysconfdir}
	install -m 0644 ${S}/nfs.conf ${D}${sysconfdir}


	install -d ${D}${systemd_system_unitdir}
	sed -i -e 's,@SBINDIR@,${sbindir},g' \
		-e 's,@SYSCONFDIR@,${sysconfdir},g' \
		-e 's,@HIGH_RLIMIT_NOFILE@,${HIGH_RLIMIT_NOFILE},g' \
		${D}${systemd_system_unitdir}/*.service

  # Force the pipefs generator
	rm -f ${D}${systemd_system_unitdir}/rpc_pipefs.target
	rm -f ${D}${systemd_system_unitdir}/var-lib-nfs-rpc_pipefs.mount

	ln -s rpc-statd.service ${D}${systemd_system_unitdir}/nfs-statd.service
	install -d ${D}${systemd_system_unitdir}/nfs-server.service.d
	printf '[Unit]\nConditionPathExists=${sysconfdir}/exports\n' > \
		${D}${systemd_system_unitdir}/nfs-server.service.d/10-require-exports.conf
	install -d ${D}${systemd_system_unitdir}/nfs-mountd.service.d
	printf '[Unit]\nConditionPathExists=${sysconfdir}/exports\n' > \
		${D}${systemd_system_unitdir}/nfs-mountd.service.d/10-require-exports.conf

	# kernel code as of 3.8 hard-codes this path as a default
	install -d ${D}/var/lib/nfs/v4recovery

	# chown the directories and files
	chown -R rpcuser:rpcuser ${D}${localstatedir}/lib/nfs/statd
	chmod 0644 ${D}${localstatedir}/lib/nfs/statd/state

	# Make python tools use python 3
	sed -i -e '1s,#!.*python.*,#!${bindir}/python3,' ${D}${sbindir}/mountstats ${D}${sbindir}/nfsiostat
}
