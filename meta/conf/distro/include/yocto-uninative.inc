#
# This include points at uninative tarballs generated and maintained
# by the Yocto Project. These file can be included by distros if they
# want to use/maintain a unified sstate feed for native/cross artefacts
# rather than the feeds having native/cross artefacts which are specific
# to the distro running on the build machine.
#

UNINATIVE_MAXGLIBCVERSION = "2.37"
#UNINATIVE_VERSION = "3.9"
UNINATIVE_VERSION = "3.9.gcc-13-2023-05-15"

UNINATIVE_URL = "file:///OE/build/uninative/${UNINATIVE_VERSION}/"
#UNINATIVE_URL ?= "http://downloads.yoctoproject.org/releases/uninative/${UNINATIVE_VERSION}/"
UNINATIVE_CHECKSUM[aarch64] ?= "163653fa954a08ba4299cd0d79269e806bfdd3d9082e92cca853630a201c1d26"
UNINATIVE_CHECKSUM[i686] ?= "7165b2be74599c5efe9d39b95608d776ce2dd0aa9d490b9f09e7dc08f27f7079"
UNINATIVE_CHECKSUM[x86_64] ?= "3dc005b561b33cfea25898538ec27532e3ca4e47e3cdaa01604cd2c2cee41430"
