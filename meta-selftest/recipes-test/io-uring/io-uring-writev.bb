DESCRIPTION = "Simple io_uring test"
SECTION = "examples"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS += "libuv-native"

SRC_URI = "file://io-uring-writev.c \
    file://task.h \
    file://libuv-fs-copyfile.c \
"

S = "${WORKDIR}"

do_compile() {
	${BUILD_CC} io-uring-writev.c -o io-uring-writev
	${BUILD_CC} -luv libuv-fs-copyfile.c -o libuv-fs-copyfile
}

do_install() {
        ${S}/io-uring-writev ${D}/test
        ${S}/libuv-fs-copyfile ${S}/task.h ${D}/task-copy.h
}

FILES:${PN} = "test test2 task-copy.h"
