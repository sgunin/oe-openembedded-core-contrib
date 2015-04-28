require clutter-1.0.inc

LIC_FILES_CHKSUM = "file://COPYING;md5=4fbd65380cdd255951079008b364516c"

SRC_URI[archive.md5sum] = "788c488d795103e4c201fae1b032cb89"
SRC_URI[archive.sha256sum] = "5225fef91f717118654a5b98e24f2018d09ca3c37d61ecff84f77069de0fbf54"

SRC_URI += "file://install-examples.patch \
            file://run-installed-tests-with-tap-output.patch \
            file://run-ptest"
