# IMAGE_INSTALL is set to union of all PACKAGES
# from all recipes listed on command line which
# are not marked as:
# EXCLUDE_FROM_WORLD or EXCLUDE_FROM_WORLD_IMAGE
# or individual packages listed in
# EXCLUDE_PACKAGES_FROM_WORLD_IMAGE
# You can use this to create "world-image" by:
# bitbake world targets-list-image

inherit targets-list-image
inherit image

LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58 \
                    file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

