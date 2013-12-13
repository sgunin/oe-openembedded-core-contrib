DESCRIPTION = "Units to make systemd work better with existing sysvinit scripts"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r18"

DEPENDS = "systemd-systemctl-native"

inherit allarch

ALLOW_EMPTY_${PN} = "1"

SYSTEMD_DISABLED_SYSV_SERVICES = " \
  busybox-udhcpc \
  hwclock \
  networking \
  syslog.busybox \
  dbus-1 \
"

pkg_postinst_${PN} () {
	cd $D${sysconfdir}/init.d

	echo "Disabling the following sysv scripts: "

	OPTS=""

	if [ -n "$D" ]; then
		OPTS="--root=$D"
	fi

	for i in ${SYSTEMD_DISABLED_SYSV_SERVICES} ; do
		if [ \( -e $i -o $i.sh \) -a ! \( -e $D${sysconfdir}/systemd/system/$i.service -o  -e $D${systemd_unitdir}/system/$i.service \) ] ; then
			echo -n "$i: " ; systemctl ${OPTS} mask $i.service
		fi
	done ; echo
}

RDPEPENDS_${PN} = "systemd"
