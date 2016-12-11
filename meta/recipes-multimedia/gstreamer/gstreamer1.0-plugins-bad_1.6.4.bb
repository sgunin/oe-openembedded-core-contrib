include gstreamer1.0-plugins-bad.inc

LIC_FILES_CHKSUM = "file://COPYING;md5=73a5855a8119deb017f5f13cf327095d \
                    file://COPYING.LIB;md5=21682e4e8fea52413fd26c60acb907e5 \
                    file://gst/tta/crc32.h;beginline=12;endline=29;md5=27db269c575d1e5317fffca2d33b3b50 \
                    file://gst/tta/filters.h;beginline=12;endline=29;md5=8a08270656f2f8ad7bb3655b83138e5a"

# Note: The mpg123 plugin was moved to gst-plugins-ugly prior to the 1.7.2
# release, so this line should be removed during the update to 1.8.x
# https://cgit.freedesktop.org/gstreamer/gst-plugins-bad/commit/?id=08d8aefcdaaf89ecb6dd53ec1e4f95cd42d01664
PACKAGECONFIG[mpg123] = "--enable-mpg123,--disable-mpg123,mpg123"

# Note: The gsettings plug-in was dropped prior to the 1.7.2 release,
# so this line should be removed during the update to 1.8.x
# https://cgit.freedesktop.org/gstreamer/gst-plugins-bad/commit/?id=914291808ad10621d6a74031f3d46d45eef5a3a3
EXTRA_OECONF += "--disable-gsettings"

SRC_URI = " \
    http://gstreamer.freedesktop.org/src/gst-plugins-bad/gst-plugins-bad-${PV}.tar.xz \
    file://configure-allow-to-disable-libssh2.patch \
    file://0001-glimagesink-Downrank-to-marginal.patch \
    file://0002-glplugin-glwindow-fix-memory-leak-of-navigation-thre.patch \
"

SRC_URI[md5sum] = "6768524cb8bcdcaf1345d9c66f3bd7bd"
SRC_URI[sha256sum] = "2b98df8d4d7784d5186baf2e19f565a6d8f8924119f8470e23c96889aaa603fe"

S = "${WORKDIR}/gst-plugins-bad-${PV}"
