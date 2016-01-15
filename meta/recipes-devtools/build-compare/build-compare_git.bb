SUMMARY = "Build Result Compare Script"
DESCRIPTION = "This package contains scripts to find out if the build result\
differs to a former build."
HOMEPAGE = "https://github.com/openSUSE/build-compare"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=751419260aa954499f7abaabaa882bbe"

PV = "2015.07.15+git${SRCPV}"

SRC_URI = "git://github.com/openSUSE/build-compare.git \
           file://Ignore-DWARF-sections.patch;striplevel=1 \
           file://0001-Add-support-for-deb-and-ipk-packaging.patch \
           "

SRCREV = "0b929c8a254b2bdb9392124dcd6836129dc125f9"

S = "${WORKDIR}/git"

BBCLASSEXTEND += "native nativesdk"

do_install() {
    install -d ${D}/${bindir}
    install -m 755 functions.sh ${D}/${bindir}
    install -m 755 pkg-diff.sh ${D}/${bindir}
    install -m 755 same-build-result.sh ${D}/${bindir}
    install -m 755 srpm-check.sh ${D}/${bindir}
}

RDEPENDS_${PN} += "bash"
