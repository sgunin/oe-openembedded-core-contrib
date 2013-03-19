SUMMARY = "Transport-Independent RPC library"
DESCRIPTION = "Libtirpc is a port of Suns Transport-Independent RPC library to Linux"
SECTION = "libs/network"
HOMEPAGE = "http://sourceforge.net/projects/libtirpc/"
BUGTRACKER = "http://sourceforge.net/tracker/?group_id=183075&atid=903784"
LICENSE = "BSD"
LIC_FILES_CHKSUM = "file://COPYING;md5=f835cce8852481e4b2bbbdd23b5e47f3 \
                    file://src/netname.c;beginline=1;endline=27;md5=f8a8cd2cb25ac5aa16767364fb0e3c24"
PR = "r0"

DEPENDS += "xz-native"
PROVIDES = "virtual/librpc"

SRC_URI = "${SOURCEFORGE_MIRROR}/${BPN}/${BPN}-${PV}.tar.bz2;name=libtirpc \
           file://libtirpc-0.2.1-fortify.patch \
           file://obsolete_automake_macros.patch \
           file://libtirpc-0.2.3-add-missing-bits-from-glibc.patch \
           file://libtirpc-0.2.3-types.h.patch \
           file://libtirpc-0008-Add-rpcgen-program-from-nfs-utils-sources.patch \
           file://key_prot.h \
           file://nis.h \
           file://nislib.h \
           file://nis_tags.h \
           file://rpc_des.h \
           file://ypclnt.h \
           file://yp_prot.h \
          "

SRC_URI_append_libc-uclibc = " file://remove-des-uclibc.patch"

SRC_URI[libtirpc.md5sum] = "b70e6c12a369a91e69fcc3b9feb23d61"
SRC_URI[libtirpc.sha256sum] = "4f29ea0491b4ca4c29f95f3c34191b857757873bbbf4b069f9dd4da01a6a923c"

inherit autotools pkgconfig

CFLAGS += "-I${S}/glibc-headers"
EXTRA_OECONF = "--disable-gss"

do_configure_prepend () {
        install -d ${S}/glibc-headers/rpc/
        install -d ${S}/glibc-headers/rpcsvc/
        
        install -m 0644 ${WORKDIR}/key_prot.h ${S}/glibc-headers/rpc
        install -m 0644 ${WORKDIR}/rpc_des.h ${S}/glibc-headers/rpc

        install -m 0644 ${WORKDIR}/nis.h ${S}/glibc-headers/rpcsvc
        install -m 0644 ${WORKDIR}/nislib.h ${S}/glibc-headers/rpcsvc
        install -m 0644 ${WORKDIR}/nis_tags.h ${S}/glibc-headers/rpcsvc
        install -m 0644 ${WORKDIR}/ypclnt.h ${S}/glibc-headers/rpcsvc
        install -m 0644 ${WORKDIR}/yp_prot.h ${S}/glibc-headers/rpcsvc
}

do_install_append() {
        chown root:root ${D}${sysconfdir}/netconfig

        install -d ${D}/${includedir}/rpc/
        install -d ${D}/${includedir}/rpcsvc/
        
        install -m 0644 ${S}/glibc-headers/rpc/*.h ${D}/${includedir}/rpc/
        install -m 0644 ${S}/glibc-headers/rpcsvc/*.h ${D}/${includedir}/rpcsvc/
}
