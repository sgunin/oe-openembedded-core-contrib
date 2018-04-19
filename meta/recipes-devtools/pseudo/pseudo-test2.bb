# Copyright (c) 2012-2018 LG Electronics, Inc.

SUMMARY = "Initialization, setup, and font files used by luna-sysmgr and luna-sysservice"
AUTHOR = "Alekseyev Oleksandr <alekseyev.oleksandr@lge.com>"
SECTION = "webos/base"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

#DEPENDS = "tzdata python-tz-native"

SRCREV = "701ec4b373b776fba829c882b05ae19de9a476ed"

inherit allarch
#inherit cmake
inherit pythonnative

SRC_URI = "git://github.com/webosose/luna-init"
S = "${WORKDIR}/git"

do_install() {
#do_install_append() {
    # Expand fonts tarball
    if [ -e ${S}/files/conf/fonts/fonts.tgz ]; then
        install -d ${D}${datadir}/fonts
        tar xvzf ${S}/files/conf/fonts/fonts.tgz --directory=${D}${datadir}/fonts
    fi
}

PACKAGES =+ "${PN}-fonts"
FILES_${PN}-fonts += "${datadir}/fonts/"
