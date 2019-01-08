SUMMARY = "Create glibc site config for speeding up do_configure"

# The LICENSE is the same as glibc
LICENSE = "GPLv2 & LGPLv2.1"
SECTION = "libs"

INHIBIT_DEFAULT_DEPS = "1"

DEPENDS = "virtual/${TARGET_PREFIX}gcc virtual/${TARGET_PREFIX}compilerlibs virtual/libc"

inherit autotools nopackages

do_compile() {
    :
}

do_install() {
    :
}

BBCLASSEXTEND = "nativesdk"
