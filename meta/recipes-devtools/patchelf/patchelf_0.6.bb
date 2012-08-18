# Copyright (C) 2010 Khem Raj <raj.khem@gmail.com>
# Released under the MIT license (see COPYING.MIT for the terms)

DESCRIPTION = "PatchELF is a small utility to modify the dynamic linker and RPATH of ELF executables."
HOMEPAGE = "http://nixos.org/patchelf.html"
LICENSE = "GPL-3.0"
SECTION = "devel"
PROVIDES += "chrpath"

LIC_FILES_CHKSUM = "file://COPYING;md5=d32239bcb673463ab874e80d47fae504"

SRCREV = "a1ddbd47d3836a5912a05439f4321db0e329fbbf"

SRC_URI = "http://hydra.nixos.org/build/1524660/download/2/patchelf-${PV}.tar.bz2"
SRC_URI[md5sum] = "5087261514b4b5814a39c3d3a36eb6ef"
SRC_URI[sha256sum] = "fc7e7fa95f282fc37a591a802629e0e1ed07bc2a8bf162228d9a69dd76127c01"

inherit autotools

BBCLASSEXTEND = "native nativesdk"
