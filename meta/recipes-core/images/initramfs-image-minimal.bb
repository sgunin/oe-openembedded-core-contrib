# Minimal initramfs image
DESCRIPTION = "Minimal initramfs image used to bring up the system."
LICENSE = "MIT"

PACKAGE_INSTALL = "initramfs-framework-base initramfs-module-udev busybox udev base-passwd"

# Do not pollute the initramfs image with rootfs features
IMAGE_FEATURES = ""
IMAGE_LINGUAS = ""

IMAGE_FSTYPES = "${INITRAMFS_FSTYPES}"
inherit image

BAD_RECOMMENDATIONS += "busybox-syslog"

# We need to set USE_DEVFS to "0" here to trigger creation of device nodes at rootfs time.
# The reason here is that, when this initramfs is bundled into kernel, we need /dev/console
# to be there before init is run.
USE_DEVFS = "0"
