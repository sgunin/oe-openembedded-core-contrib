#
# This include points at uninative tarballs generated and maintained
# by the Yocto Project. These file can be included by distros if they
# want to use/maintain a unified sstate feed for native/cross artefacts
# rather than the feeds having native/cross artefacts which are specific
# to the distro running on the build machine.
#

UNINATIVE_MAXGLIBCVERSION = "2.37"
#UNINATIVE_VERSION = "3.9"
UNINATIVE_VERSION = "3.9.gcc-13-2023-05-26"

UNINATIVE_URL = "file:///OE/build/uninative/${UNINATIVE_VERSION}/"
#UNINATIVE_URL ?= "http://downloads.yoctoproject.org/releases/uninative/${UNINATIVE_VERSION}/"
UNINATIVE_CHECKSUM[aarch64] ?= "cc881b6a09e46099ec7e375bca614bc7a182ea1684c4ada38a386510d05409ea"
UNINATIVE_CHECKSUM[i686] ?= "25824700bdc7c19fda3234436c4f8efe92e623babb32810c59442b57e2900bc1"
UNINATIVE_CHECKSUM[x86_64] ?= "87e0b301173fa295bfca9aa70a02b75ce3941ee6a3c4a0011f1a6c2a65c5da5d"
