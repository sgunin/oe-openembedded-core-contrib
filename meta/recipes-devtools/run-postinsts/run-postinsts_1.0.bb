DESCRIPTION = "Run postinstall scripts on device"
SECTION = "devel"
PR = "r9"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58 \
                    file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

SRC_URI = "file://run-postinsts \
           file://run-postinsts.init \
           file://run-postinsts.service"

inherit allarch systemd update-rc.d

INITSCRIPT_NAME = "run-postinsts"
INITSCRIPT_PARAMS = "start 99 S ."

SYSTEMD_SERVICE_${PN} = "run-postinsts.service"

do_configure() {
	:
}

do_compile () {
	:
}

do_install() {
	install -d ${D}${sbindir}
	install -m 0755 ${WORKDIR}/run-postinsts ${D}${sbindir}/

	install -d ${D}${sysconfdir}/init.d/
	install -m 0755 ${WORKDIR}/run-postinsts.init ${D}${sysconfdir}/init.d/run-postinsts

	install -d ${D}${systemd_unitdir}/system/
	install -m 0644 ${WORKDIR}/run-postinsts.service ${D}${systemd_unitdir}/system/

	sed -i -e 's:#SYSCONFDIR#:${sysconfdir}:g' \
               -e 's:#SBINDIR#:${sbindir}:g' \
               -e 's:#BASE_BINDIR#:${base_bindir}:g' \
               -e 's:#IMAGE_PKGTYPE#:${IMAGE_PKGTYPE}:g' \
               -e 's:#PM_INSTALLED#:${@base_contains("IMAGE_FEATURES", "package-management", "true", "false", d)}:g' \
               ${D}${sbindir}/run-postinsts \
               ${D}${systemd_unitdir}/system/run-postinsts.service
}
