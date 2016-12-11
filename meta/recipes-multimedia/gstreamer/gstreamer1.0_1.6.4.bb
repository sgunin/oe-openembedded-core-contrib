include gstreamer1.0.inc

LIC_FILES_CHKSUM = "file://COPYING;md5=6762ed442b3822387a51c92d928ead0d \
                    file://gst/gst.h;beginline=1;endline=21;md5=e059138481205ee2c6fc1c079c016d0d"

SRC_URI = " \
    http://gstreamer.freedesktop.org/src/gstreamer/gstreamer-${PV}.tar.xz \
    file://0001-Fix-crash-with-gst-inspect.patch \
"

SRC_URI[md5sum] = "fb2fbf42db73ef736bdf39f50d63dd66"
SRC_URI[sha256sum] = "4ccba88a286b13d6f2d8c1180f78a13dcd49f2fc3cb2b3b3f502b3a23f7c01b5"

S = "${WORKDIR}/gstreamer-${PV}"
