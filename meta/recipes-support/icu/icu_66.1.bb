require icu.inc

LIC_FILES_CHKSUM = "file://../LICENSE;md5=a3808a5b70071b07f87ff2205e4d75a0"

def icu_download_version(d):
    pvsplit = d.getVar('PV').split('.')
    return pvsplit[0] + "_" + pvsplit[1]

def icu_download_folder(d):
    pvsplit = d.getVar('PV').split('.')
    return pvsplit[0] + "-" + pvsplit[1]

ICU_PV = "${@icu_download_version(d)}"
ICU_FOLDER = "${@icu_download_folder(d)}"

# http://errors.yoctoproject.org/Errors/Details/20486/
ARM_INSTRUCTION_SET_armv4 = "arm"
ARM_INSTRUCTION_SET_armv5 = "arm"

# 66-1 SRCREV = "5f681ecbc75898a6484217b322f3883b6d1b2049"
SRCREV = "440b1cd9d29b33276365ac83f242f8dc137fd35b"
SRC_URI = "git://github.com/unicode-org/icu.git \
           file://0002-ICU-21175-Add-cnvalias-as-a-dependency-of-misc_res.patch \
           file://filter.json \
           file://0001-icu-fix-install-race.patch \
           file://0002-icu-Added-armeb-support.patch \
           file://0005-ICU-21015-Fixing-gcc-compiler-warnings.patch \
           file://0007-ICU-21026-fix-GCC-warnings-of-signed-int-left-shift.patch \
           "
S = "${WORKDIR}/git/icu4c/source"

SRC_URI_append_class-target = "\
           file://0008-Disable-LDFLAGSICUDT-for-Linux.patch \
          "

UPSTREAM_CHECK_REGEX = "icu4c-(?P<pver>\d+(_\d+)+)-src"
UPSTREAM_CHECK_URI = "https://github.com/unicode-org/icu/releases"

EXTRA_OECONF_append_libc-musl = " ac_cv_func_strtod_l=no"

PACKAGECONFIG ?= ""
PACKAGECONFIG[make-icudata] = ",,,"

do_make_icudata_class-target () {
    ${@bb.utils.contains('PACKAGECONFIG', 'make-icudata', '', 'exit 0', d)}
    cd ${S}
    AR='${BUILD_AR}' \
    CC='${BUILD_CC}' \
    CPP='${BUILD_CPP}' \
    CXX='${BUILD_CXX}' \
    RANLIB='${BUILD_RANLIB}' \
    CFLAGS='${BUILD_CFLAGS}' \
    CPPFLAGS='${BUILD_CPPFLAGS}' \
    CXXFLAGS='${BUILD_CXXFLAGS}' \
    LDFLAGS='${BUILD_LDFLAGS}' \
    ICU_DATA_FILTER_FILE=${WORKDIR}/filter.json \
    ./runConfigureICU Linux --with-data-packaging=archive
    oe_runmake
    install -Dm644 ${S}/data/out/icudt${ICU_MAJOR_VER}l.dat ${S}/data/in/icudt${ICU_MAJOR_VER}l.dat
}

do_make_icudata() {
    :
}

addtask make_icudata before do_configure after do_patch
