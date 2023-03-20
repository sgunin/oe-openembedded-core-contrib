#
# This include points at uninative tarballs generated and maintained
# by the Yocto Project. These file can be included by distros if they
# want to use/maintain a unified sstate feed for native/cross artefacts
# rather than the feeds having native/cross artefacts which are specific
# to the distro running on the build machine.
#

UNINATIVE_MAXGLIBCVERSION = "2.37"
#UNINATIVE_VERSION = "3.9"
UNINATIVE_VERSION = "3.9.gcc-13"

UNINATIVE_URL = "file:///OE/build/uninative/${UNINATIVE_VERSION}/"
#UNINATIVE_URL ?= "http://downloads.yoctoproject.org/releases/uninative/${UNINATIVE_VERSION}/"
#UNINATIVE_CHECKSUM[aarch64] ?= "de35708c95c34573af140da910132c3291ba4fd26ebf7b74b755ada432cdf07b"
#UNINATIVE_CHECKSUM[i686] ?= "adac07b08adb88eb26fc7fd87fee0cec9d5be167bf7c5ffd3a549a2a6699c29c"
UNINATIVE_CHECKSUM[x86_64] ?= "c7a63f970ebbb6db3c3d63ea1dd1984df09306c3380166c56f60b8a5c25b7b6b"
