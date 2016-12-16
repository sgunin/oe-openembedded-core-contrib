# cdrtools-native OE build file
# Copyright (C) 2004-2006, Advanced Micro Devices, Inc.  All Rights Reserved
# Released under the MIT license (see packages/COPYING)
SUMMARY = "A set of tools for CD recording, including cdrecord"
HOMEPAGE = "http://sourceforge.net/projects/cdrtools/"
SECTION = "console/utils"
LICENSE = "GPLv2 & CDDL-1.0 & LGPLv2.1+"
LIC_FILES_CHKSUM = "file://COPYING;md5=32f68170be424c2cd64804337726b312"

SRC_URI = "${SOURCEFORGE_MIRROR}/project/cdrtools/alpha/cdrtools-${PV}.tar.bz2"

SRC_URI[md5sum] = "ea362a6a42d8aa0d5fc154d195f47926"
SRC_URI[sha256sum] = "49c1a67fa7ad3d7c0b05d41d18cb6677b40d4811faba111f0c01145d3ef0491b"

EXTRA_OEMAKE = "-e MAKEFLAGS="

inherit native

PV = "3.02a07"
REALPV = "3.02"

S = "${WORKDIR}/${BPN}-${REALPV}"

do_install() {
	make install GMAKE_NOWARN=true INS_BASE=${prefix} DESTDIR=${D}
}
