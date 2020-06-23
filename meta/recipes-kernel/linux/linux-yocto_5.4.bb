KBRANCH ?= "v5.4/standard/base"

require recipes-kernel/linux/linux-yocto.inc

# board specific branches
KBRANCH_qemuarm  ?= "v5.4/standard/arm-versatile-926ejs"
KBRANCH_qemuarm64 ?= "v5.4/standard/qemuarm64"
KBRANCH_qemumips ?= "v5.4/standard/mti-malta32"
KBRANCH_qemuppc  ?= "v5.4/standard/qemuppc"
KBRANCH_qemuriscv64  ?= "v5.4/standard/base"
KBRANCH_qemux86  ?= "v5.4/standard/base"
KBRANCH_qemux86-64 ?= "v5.4/standard/base"
KBRANCH_qemumips64 ?= "v5.4/standard/mti-malta64"

SRCREV_machine_qemuarm ?= "6c628db39bf48f7dd6cd95ae826bcaa18a56df1d"
SRCREV_machine_qemuarm64 ?= "9e1b13d7f9d84f691fb9988c5f53c5ab62b8a5e9"
SRCREV_machine_qemumips ?= "c0cd2937ae195344ece04663b30b2049427b3c57"
SRCREV_machine_qemuppc ?= "9e1b13d7f9d84f691fb9988c5f53c5ab62b8a5e9"
SRCREV_machine_qemuriscv64 ?= "9e1b13d7f9d84f691fb9988c5f53c5ab62b8a5e9"
SRCREV_machine_qemux86 ?= "9e1b13d7f9d84f691fb9988c5f53c5ab62b8a5e9"
SRCREV_machine_qemux86-64 ?= "9e1b13d7f9d84f691fb9988c5f53c5ab62b8a5e9"
SRCREV_machine_qemumips64 ?= "6254ff1d776b75bc3d9c2c66c083fbc622091cd4"
SRCREV_machine ?= "9e1b13d7f9d84f691fb9988c5f53c5ab62b8a5e9"
SRCREV_meta ?= "aafb8f095e97013d6e55b09ed150369cbe0c6476"

# remap qemuarm to qemuarma15 for the 5.4 kernel
# KMACHINE_qemuarm ?= "qemuarma15"

SRC_URI = "git://git.yoctoproject.org/linux-yocto.git;name=machine;branch=${KBRANCH}; \
           git://git.yoctoproject.org/yocto-kernel-cache;type=kmeta;name=meta;branch=yocto-5.4;destsuffix=${KMETA}"

SRC_URI_append_qemuppc64 = " file://defconfig"
CFLAGS += "-Wno-error"

LIC_FILES_CHKSUM = "file://COPYING;md5=bbea815ee2795b2f4230826c0c6b8814"
LINUX_VERSION ?= "5.4.43"

DEPENDS += "${@bb.utils.contains('ARCH', 'x86', 'elfutils-native', '', d)}"
DEPENDS += "openssl-native util-linux-native"

PV = "${LINUX_VERSION}+git${SRCPV}"

KMETA = "kernel-meta"
KCONF_BSP_AUDIT_LEVEL = "2"

KERNEL_DEVICETREE_qemuarmv5 = "versatile-pb.dtb"

COMPATIBLE_MACHINE = "qemuarm|qemuarmv5|qemuarm64|qemux86|qemuppc|qemuppc64|qemumips|qemumips64|qemux86-64|qemuriscv64"

# Functionality flags
KERNEL_EXTRA_FEATURES ?= "features/netfilter/netfilter.scc"
KERNEL_FEATURES_append = " ${KERNEL_EXTRA_FEATURES}"
KERNEL_FEATURES_append_qemuall=" cfg/virtio.scc features/drm-bochs/drm-bochs.scc"
KERNEL_FEATURES_append_qemux86=" cfg/sound.scc cfg/paravirt_kvm.scc"
KERNEL_FEATURES_append_qemux86-64=" cfg/sound.scc cfg/paravirt_kvm.scc"
KERNEL_FEATURES_append = " ${@bb.utils.contains("TUNE_FEATURES", "mx32", " cfg/x32.scc", "" ,d)}"
KERNEL_FEATURES_append = " ${@bb.utils.contains("DISTRO_FEATURES", "ptest", " features/scsi/scsi-debug.scc", "" ,d)}"
