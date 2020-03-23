LTO_pn-glibc = ""
LTO_pn-gcc-runtime = ""
LTO_pn-libgcc-initial = ""
LTO_pn-libgcc = ""
LTO_pn-libpam = ""
LTO_pn-elfutils = ""
LTO_pn-perl = ""
LTO_pn-busybox = ""
LTO_pn-libxcrypt = ""
LTO_pn-curl = ""
LTO_pn-libcap = ""
LTO_pn-python3 = ""
LTO_pn-libproxy = ""
LTO_pn-libbsd = ""

LTO ?= "-flto"

SELECTED_OPTIMIZATION_append_class-target = " ${LTO}"
TARGET_LDFLAGS_append_class-target = " ${LTO}"

SELECTED_OPTIMIZATION[vardeps] += "LTO"

PACKAGE_DEBUG_SPLIT_STYLE = "debug-without-src"
