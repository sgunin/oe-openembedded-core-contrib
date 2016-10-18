require python_${PV}.bb

SRC_URI += "file://rename-libpython-to-libpython-profile-opt.patch"

# Use special prefix in order to prevent clash with normal python
STAGING_INCDIR_DEFAULT = "${STAGING_DIR_HOST}/usr/include"
STAGING_LIBDIR_DEFAULT = "${STAGING_DIR_HOST}/usr/${baselib}"
TARGET_CFLAGS += "-I${STAGING_INCDIR_DEFAULT}"
TARGET_CPPFLAGS += "-I${STAGING_INCDIR_DEFAULT}"
prefix = "/opt"
exec_prefix = "/opt"

PYTHON_MAKE_TARGET = "build_all_generate_profile"
