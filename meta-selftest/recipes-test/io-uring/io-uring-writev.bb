DESCRIPTION = "Simple io_uring test"
SECTION = "examples"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://io-uring-writev.c"

S = "${WORKDIR}"

do_compile() {
	${BUILD_CC} io-uring-writev.c -o io-uring-writev
}

do_install() {
        ${S}/io-uring-writev ${D}/test
}

FILES:${PN} = "test"
