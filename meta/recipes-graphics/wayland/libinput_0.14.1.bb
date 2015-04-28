SUMMARY = "Library to handle input devices in Wayland compositors"
HOMEPAGE = "http://www.freedesktop.org/wiki/Software/libinput/"
SECTION = "libs"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=673e626420c7f859fbe2be3a9c13632d"

DEPENDS = "libevdev udev mtdev"

SRC_URI = "http://www.freedesktop.org/software/${BPN}/${BP}.tar.xz"
SRC_URI[md5sum] = "18f6e1d6ab58db9a66c5ee8ca20aa876"
SRC_URI[sha256sum] = "2bed202ebe2d5026950d6f9d2ac0f0160d12f61c5a0f6d0d6ef671bbb02c1b64"

inherit autotools pkgconfig

FILES_${PN} += "${libdir}/udev/libinput-device-group"

FILES_${PN}-dbg += "${libdir}/udev/.debug/*"
