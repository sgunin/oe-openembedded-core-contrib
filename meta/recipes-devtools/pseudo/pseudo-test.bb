LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS = "qtbase-native"
CLEANBROKEN = "1"

inherit qmake5_paths

RULES = "999"
PARALLEL_MAKE = "-j 30"

do_compile() {
    # with normall install command it doesn't trigger the issue
    # echo "QINSTALL      = ${STAGING_BINDIR_NATIVE}/install" > Makefile
    /bin/echo "QINSTALL      = ${OE_QMAKE_PATH_EXTERNAL_HOST_BINS}/qmake -install qinstall" > Makefile
    ALL="all: "
    for i in `seq -w 1 ${RULES}`; do
        /bin/echo $i > ${S}/$i.txt
        /bin/echo -e "R$i:\n\t\$(QINSTALL) ${S}/$i.txt ${D}/$i.txt && mv ${D}/$i.txt ${D}/`expr $i + 1`.txt" >> Makefile;
        ALL="$ALL R$i"
    done
    /bin/echo ${ALL} >> Makefile
}

do_install() {
    oe_runmake all
}

FILES_${PN} = "/"
