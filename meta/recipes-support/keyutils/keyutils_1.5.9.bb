SUMMARY = "Linux Key Management Utilities"
DESCRIPTION = "Keyutils is a set of utilities for managing the key retention \
facility in the kernel, which can be used by filesystems, block devices and \
more to gain and retain the authorization and encryption keys required to \
perform secure operations."
HOMEPAGE = "https://people.redhat.com/~dhowells/keyutils/"
LICENSE = "GPLv2+ & LGPLv2.1+"
LICENSE_${PN} = "GPLv2+"
LICENSE_lib${PN} = "LGPLv2.1+"
LIC_FILES_CHKSUM = "file://LICENCE.GPL;md5=5f6e72824f5da505c1f4a7197f004b45 \
                    file://LICENCE.LGPL;md5=7d1cacaa3ea752b72ea5e525df54a21f"

SRC_URI = "https://people.redhat.com/~dhowells/keyutils/${BP}.tar.bz2 \
           file://0001-Include-limits.h-for-UINT_MAX.patch"
SRC_URI[md5sum] = "7f8ac985c45086b5fbcd12cecd23cf07"
SRC_URI[sha256sum] = "4da2c5552c688b65ab14d4fd40fbdf720c8b396d8ece643e040cf6e707e083ae"

EXTRA_OEMAKE = " \
    DESTDIR=${D} \
    ETCDIR=${sysconfdir} \
    BINDIR=${bindir} \
    SBINDIR=${sbindir} \
    SHAREDIR=${datadir}/${BPN} \
    MANDIR=${mandir} \
    INCLUDEDIR=${includedir} \
    LIBDIR=${base_libdir} \
    USRLIBDIR=${libdir} \
    BUILDFOR= \
"

do_install() {
    oe_runmake install
}

PACKAGES =+ "lib${PN}"

FILES_lib${PN} = "${base_libdir}/lib*${SOLIBS}"
