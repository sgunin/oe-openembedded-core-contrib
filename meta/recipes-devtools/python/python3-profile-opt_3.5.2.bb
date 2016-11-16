require python3_${PV}.bb

SRC_URI += "file://Makefile-add-install_generate_profile-target.patch \
           "

PYTHON3_MAKE_TARGET = "build_all_generate_profile"

RCONFLICTS_${PN}-core = "python3-core"
RCONFLICTS_lib${BPN} = "libpython3"

# Prevent a clash with libpython3
EXCLUDE_FROM_SHLIBS = "1"
RDEPENDS_${PN}-core += "lib${BPN}"
DEBIAN_NOAUTONAME_lib${BPN} = "1"
DEBIAN_NOAUTONAME_lib${BPN}-staticdev = "1"
