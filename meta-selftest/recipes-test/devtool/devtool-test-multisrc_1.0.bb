SUMMARY = "Recipe to test multiple source trees"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

SRC_URI = "file://mypackage-${PV}.tar.gz \
           file://example-files.tar.gz \
           file://example.patch;patchdir=../example-files \
"

S = "${WORKDIR}/mypackage-${PV}"

