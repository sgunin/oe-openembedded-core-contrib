SUMMARY = "Recipe to test multiple source trees"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://settings-daemon.c;beginline=1;endline=26;md5=8d77ba1c7a55df48d8d06c5f3d86b49d"

SRC_URI = "git://git.yoctoproject.org/xsettings-daemon;name=xsettings-daemon \
           git://git.yoctoproject.org/libfakekey;name=libfakekey;destsuffix=git/libfakekey \
"

SRCREV_xsettings-daemon = "b2e5da502f8c5ff75e9e6da771372ef8e40fd9a2"
SRCREV_libfakekey = "7ad885912efb2131e80914e964d5e635b0d07b40"

S = "${WORKDIR}/git"

