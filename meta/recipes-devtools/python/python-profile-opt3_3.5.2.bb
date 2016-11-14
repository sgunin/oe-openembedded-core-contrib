require python3_${PV}.bb

SRC_URI += "file://rename-libpython3-to-libpython-profile-opt3.patch \
            file://Makefile-add-install_generate_profile-target.patch \
           "

# Use special prefix in order to prevent clash with the normal python3 package
STAGING_INCDIR_DEFAULT = "${STAGING_DIR_HOST}/usr/include"
STAGING_LIBDIR_DEFAULT = "${STAGING_DIR_HOST}/usr/${baselib}"
TARGET_CFLAGS += "-I${STAGING_INCDIR_DEFAULT}"
TARGET_CPPFLAGS += "-I${STAGING_INCDIR_DEFAULT}"
prefix = "/opt"
exec_prefix = "/opt"

PYTHON3_MAKE_TARGET = "build_all_generate_profile"
