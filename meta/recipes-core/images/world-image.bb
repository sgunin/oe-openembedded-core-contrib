# IMAGE_INSTALL is set from bitbake itself to union of all PACKAGES
# from all recipes which are not marked as:
# EXCLUDE_FROM_WORLD or EXCLUDE_FROM_WORLD_IMAGE
# or individual packages listed in
# EXCLUDE_PACKAGES_FROM_WORLD_IMAGE

inherit image

# don't build this in regular world builds
EXCLUDE_FROM_WORLD = "1"
