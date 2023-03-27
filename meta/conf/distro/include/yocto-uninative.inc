#
# This include points at uninative tarballs generated and maintained
# by the Yocto Project. These file can be included by distros if they
# want to use/maintain a unified sstate feed for native/cross artefacts
# rather than the feeds having native/cross artefacts which are specific
# to the distro running on the build machine.
#

UNINATIVE_MAXGLIBCVERSION = "2.37"
#UNINATIVE_VERSION = "3.9"
UNINATIVE_VERSION = "3.9.gcc-13-2023-03-27"

UNINATIVE_URL = "file:///OE/build/uninative/${UNINATIVE_VERSION}/"
#UNINATIVE_URL ?= "http://downloads.yoctoproject.org/releases/uninative/${UNINATIVE_VERSION}/"
UNINATIVE_CHECKSUM[aarch64] ?= "2ec5180dc8b2b931c61a0745def085e8721c3332784528242c52c23731d13c4a"
UNINATIVE_CHECKSUM[i686] ?= "15637953000ee828bd69eaa2148a247ff67a859d30f8453b6ec0b5b416ee07f1"
UNINATIVE_CHECKSUM[x86_64] ?= "6ef671d46d91e2429a5f38090e6a6c9f29368c990e833c0edb3fe2a62782bac3"
