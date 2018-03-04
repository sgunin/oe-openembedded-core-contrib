LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit allarch

DEPENDS = "coreutils-native"
CLEANBROKEN = "1"

RULES = "1000"

do_compile() {
    echo > Makefile; ALL="all: "; for i in `seq -w 1 ${RULES}`; do echo -e "R$i:\n\techo $i > foo.txt" >> Makefile; ALL="$ALL R$i"; done; echo ${ALL} >> Makefile
}

do_install() {
    oe_runmake all
    cat foo.txt
    mv foo.txt ${D}
}

FILES_${PN} = "/"
