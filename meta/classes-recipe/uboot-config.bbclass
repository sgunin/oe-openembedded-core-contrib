# Handle U-Boot config for a machine
#
# The format to specify it, in the machine, is:
#
# UBOOT_CONFIG ??= <default>
# UBOOT_CONFIG[foo] = "config,images,binary"
#
# or
#
# UBOOT_MACHINE = "config"
#
# Copyright 2013, 2014 (C) O.S. Systems Software LTDA.
#
# SPDX-License-Identifier: MIT


def removesuffix(s, suffix):
    if suffix and s.endswith(suffix):
        return s[:-len(suffix)]
    return s

inherit kernel-artifact-names

UBOOT_VERSION_SUFFIX ?= "${IMAGE_VERSION_SUFFIX}"
UBOOT_ARTIFACT_NAME ?= "${IMAGE_MACHINE_SUFFIX}"
UBOOT_ARTIFACT_LINK_NAME ?= "${UBOOT_ARTIFACT_NAME}${UBOOT_VERSION_SUFFIX}"

UBOOT_ENTRYPOINT ?= "20008000"
UBOOT_LOADADDRESS ?= "${UBOOT_ENTRYPOINT}"

# Some versions of u-boot use .bin and others use .img.  By default use .bin
# but enable individual recipes to change this value.
UBOOT_SUFFIX ??= "bin"
UBOOT_BINARY ?= "u-boot.${UBOOT_SUFFIX}"
UBOOT_BINARYNAME ?= "${@os.path.splitext(d.getVar("UBOOT_BINARY"))[0]}"
UBOOT_IMAGE ?= "${UBOOT_BINARYNAME}${UBOOT_ARTIFACT_NAME}.${UBOOT_SUFFIX}"
UBOOT_LINK ?= "${UBOOT_BINARYNAME}${UBOOT_ARTIFACT_LINK_NAME}.${UBOOT_SUFFIX}"
UBOOT_MAKE_TARGET ?= "all"

# Output the ELF generated. Some platforms can use the ELF file and directly
# load it (JTAG booting, QEMU) additionally the ELF can be used for debugging
# purposes.
UBOOT_ELF ?= ""
UBOOT_ELF_SUFFIX ?= "elf"
UBOOT_ELF_IMAGE ?= "u-boot-${MACHINE}-${PV}-${PR}.${UBOOT_ELF_SUFFIX}"
UBOOT_ELF_BINARY ?= "u-boot.${UBOOT_ELF_SUFFIX}"
UBOOT_ELF_LINK ?= "u-boot${UBOOT_ARTIFACT_LINK_NAME}.${UBOOT_ELF_SUFFIX}"

# Some versions of u-boot build an SPL (Second Program Loader) image that
# should be packaged along with the u-boot binary as well as placed in the
# deploy directory.  For those versions they can set the following variables
# to allow packaging the SPL.
SPL_SUFFIX ?= ""
SPL_BINARY ?= ""
SPL_DELIMITER  ?= "${@'.' if d.getVar("SPL_SUFFIX") else ''}"
SPL_BINARYFILE ?= "${@os.path.basename(d.getVar("SPL_BINARY"))}"
SPL_BINARYNAME ?= "${@removesuffix(d.getVar("SPL_BINARYFILE"), "." + d.getVar("SPL_SUFFIX"))}"
SPL_IMAGE ?= "${SPL_BINARYNAME}${UBOOT_ARTIFACT_NAME}${SPL_DELIMITER}${SPL_SUFFIX}"
SPL_LINK ?= "${SPL_BINARYNAME}${UBOOT_ARTIFACT_LINK_NAME}${SPL_DELIMITER}${SPL_SUFFIX}"

# Additional environment variables or a script can be installed alongside
# u-boot to be used automatically on boot.  This file, typically 'uEnv.txt'
# or 'boot.scr', should be packaged along with u-boot as well as placed in the
# deploy directory.  Machine configurations needing one of these files should
# include it in the SRC_URI and set the UBOOT_ENV parameter.
UBOOT_ENV_SUFFIX ?= "txt"
UBOOT_ENV ?= ""
UBOOT_ENV_SRC_SUFFIX ?= "cmd"
UBOOT_ENV_SRC ?= "${UBOOT_ENV}.${UBOOT_ENV_SRC_SUFFIX}"
UBOOT_ENV_BINARY ?= "${UBOOT_ENV}.${UBOOT_ENV_SUFFIX}"
UBOOT_ENV_IMAGE ?= "${UBOOT_ENV}${UBOOT_ARTIFACT_NAME}.${UBOOT_ENV_SUFFIX}"
UBOOT_ENV_LINK ?= "${UBOOT_ENV}${UBOOT_ARTIFACT_LINK_NAME}.${UBOOT_ENV_SUFFIX}"

# U-Boot EXTLINUX variables. U-Boot searches for /boot/extlinux/extlinux.conf
# to find EXTLINUX conf file.
UBOOT_EXTLINUX_INSTALL_DIR ?= "/boot/extlinux"
UBOOT_EXTLINUX_CONF_NAME ?= "extlinux.conf"
UBOOT_EXTLINUX_CONF_LINK ?= "${UBOOT_EXTLINUX_CONF_NAME}${UBOOT_ARTIFACT_LINK_NAME}"

# Options for the device tree compiler passed to mkimage '-D' feature:
UBOOT_MKIMAGE_DTCOPTS ??= ""
SPL_MKIMAGE_DTCOPTS ??= ""

# mkimage command
UBOOT_MKIMAGE ?= "uboot-mkimage"
UBOOT_MKIMAGE_SIGN ?= "${UBOOT_MKIMAGE}"

# Signature activation - this requires KERNEL_IMAGETYPE = "fitImage"
UBOOT_SIGN_ENABLE ?= "0"

# Arguments passed to mkimage for signing
UBOOT_MKIMAGE_SIGN_ARGS ?= ""
SPL_MKIMAGE_SIGN_ARGS ?= ""

# Options to deploy the u-boot device tree
UBOOT_DTB ?= ""
UBOOT_DTB_BINARY ??= ""

# uboot-fit_check_sign command
UBOOT_FIT_CHECK_SIGN ?= "uboot-fit_check_sign"

python () {
    ubootmachine = d.getVar("UBOOT_MACHINE")
    ubootconfigflags = d.getVarFlags('UBOOT_CONFIG')
    ubootbinary = d.getVar('UBOOT_BINARY')
    ubootbinaries = d.getVar('UBOOT_BINARIES')
    # The "doc" varflag is special, we don't want to see it here
    ubootconfigflags.pop('doc', None)
    ubootconfig = (d.getVar('UBOOT_CONFIG') or "").split()

    if not ubootmachine and not ubootconfig:
        PN = d.getVar("PN")
        FILE = os.path.basename(d.getVar("FILE"))
        bb.debug(1, "To build %s, see %s for instructions on \
                 setting up your machine config" % (PN, FILE))
        raise bb.parse.SkipRecipe("Either UBOOT_MACHINE or UBOOT_CONFIG must be set in the %s machine configuration." % d.getVar("MACHINE"))

    if ubootmachine and ubootconfig:
        raise bb.parse.SkipRecipe("You cannot use UBOOT_MACHINE and UBOOT_CONFIG at the same time.")

    if ubootconfigflags and ubootbinaries:
        raise bb.parse.SkipRecipe("You cannot use UBOOT_BINARIES as it is internal to uboot_config.bbclass.")

    if len(ubootconfig) > 0:
        for config in ubootconfig:
            found = False
            for f, v in ubootconfigflags.items():
                if config == f: 
                    found = True
                    items = v.split(',')
                    if items[0] and len(items) > 3:
                        raise bb.parse.SkipRecipe('Only config,images,binary can be specified!')
                    d.appendVar('UBOOT_MACHINE', ' ' + items[0])
                    # IMAGE_FSTYPES appending
                    if len(items) > 1 and items[1]:
                        bb.debug(1, "Appending '%s' to IMAGE_FSTYPES." % items[1])
                        d.appendVar('IMAGE_FSTYPES', ' ' + items[1])
                    if len(items) > 2 and items[2]:
                        bb.debug(1, "Appending '%s' to UBOOT_BINARIES." % items[2])
                        d.appendVar('UBOOT_BINARIES', ' ' + items[2])
                    else:
                        bb.debug(1, "Appending '%s' to UBOOT_BINARIES." % ubootbinary)
                        d.appendVar('UBOOT_BINARIES', ' ' + ubootbinary)
                    break

            if not found:
                raise bb.parse.SkipRecipe("The selected UBOOT_CONFIG key %s has no match in %s." % (ubootconfig, ubootconfigflags.keys()))

            if len(ubootconfig) == 1:
                d.setVar('KCONFIG_CONFIG_ROOTDIR', os.path.join(d.getVar("B"), d.getVar("UBOOT_MACHINE").strip()))
            else:
                # Disable menuconfig for multiple configs
                d.setVar('KCONFIG_CONFIG_ENABLE_MENUCONFIG', "false")
}
