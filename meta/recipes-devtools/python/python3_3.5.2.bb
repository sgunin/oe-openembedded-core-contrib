require recipes-devtools/python/python.inc

DEPENDS = "python3-native libffi bzip2 db gdbm openssl readline sqlite3 zlib virtual/libintl xz"
PR = "${INC_PR}.0"
PYTHON_MAJMIN = "3.5"
PYTHON_BINABI= "${PYTHON_MAJMIN}m"
DISTRO_SRC_URI ?= "file://sitecustomize.py"
DISTRO_SRC_URI_linuxstdbase = ""
SRC_URI = "http://www.python.org/ftp/python/${PV}/Python-${PV}.tar.xz \
file://python-config.patch \
file://000-cross-compile.patch \
file://030-fixup-include-dirs.patch \
file://070-dont-clean-ipkg-install.patch \
file://080-distutils-dont_adjust_files.patch \
file://130-readline-setup.patch \
file://150-fix-setupterm.patch \
file://0001-h2py-Fix-issue-13032-where-it-fails-with-UnicodeDeco.patch \
file://tweak-MULTIARCH-for-powerpc-linux-gnuspe.patch \
${DISTRO_SRC_URI} \
"

SRC_URI += "\
            file://03-fix-tkinter-detection.patch \
            file://avoid_warning_about_tkinter.patch \
            file://cgi_py.patch \
            file://host_include_contamination.patch \
            file://python-3.3-multilib.patch \
            file://shutil-follow-symlink-fix.patch \
            file://sysroot-include-headers.patch \
            file://unixccompiler.patch \
            file://avoid-ncursesw-include-path.patch \
            file://python3-use-CROSSPYTHONPATH-for-PYTHON_FOR_BUILD.patch \
            file://sysconfig.py-add-_PYTHON_PROJECT_SRC.patch \
            file://setup.py-check-cross_compiling-when-get-FLAGS.patch \
            file://configure.ac-fix-LIBPL.patch \
            file://python3-fix-CVE-2016-1000110.patch \
            file://upstream-random-fixes.patch \
            file://Use-correct-CFLAGS-for-extensions-when-cross-compili.patch \
           "
SRC_URI[md5sum] = "8906efbacfcdc7c3c9198aeefafd159e"
SRC_URI[sha256sum] = "0010f56100b9b74259ebcd5d4b295a32324b58b517403a10d1a2aa7cb22bca40"

LIC_FILES_CHKSUM = "file://LICENSE;md5=6b60258130e4ed10d3101517eb5b9385"

# exclude pre-releases for both python 2.x and 3.x
UPSTREAM_CHECK_REGEX = "[Pp]ython-(?P<pver>\d+(\.\d+)+).tar"

S = "${WORKDIR}/Python-${PV}"

inherit autotools multilib_header python3native pkgconfig

CONFIGUREOPTS += " --with-system-ffi "

CACHED_CONFIGUREVARS = "ac_cv_have_chflags=no \
                ac_cv_have_lchflags=no \
                ac_cv_have_long_long_format=yes \
                ac_cv_buggy_getaddrinfo=no \
                ac_cv_file__dev_ptmx=yes \
                ac_cv_file__dev_ptc=no \
"

TARGET_CC_ARCH += "-DNDEBUG -fno-inline"
SDK_CC_ARCH += "-DNDEBUG -fno-inline"
EXTRA_OEMAKE += "CROSS_COMPILE=yes"
EXTRA_OECONF += "CROSSPYTHONPATH=${STAGING_LIBDIR_NATIVE}/python${PYTHON_MAJMIN}/lib-dynload/ --without-ensurepip"

export CROSS_COMPILE = "${TARGET_PREFIX}"
export _PYTHON_PROJECT_BASE = "${B}"
export _PYTHON_PROJECT_SRC = "${S}"
export CCSHARED = "-fPIC"

# These enable build with modified prefix (used in python3-prorile-opt recipe)
STAGING_INCDIR_DEFAULT ?= "${STAGING_INCDIR}"
STAGING_LIBDIR_DEFAULT ?= "${STAGING_LIBDIR}"

# Fix cross compilation of different modules
export CROSSPYTHONPATH = "${STAGING_LIBDIR_NATIVE}/python${PYTHON_MAJMIN}/lib-dynload/:${B}/build/lib.linux-${TARGET_ARCH}-${PYTHON_MAJMIN}:${S}/Lib:${S}/Lib/plat-linux"

# No ctypes option for python 3
PYTHONLSBOPTS = ""

# Automatic profile guided optimization
PYTHON3_MAKE_TARGET ?= "${@'build_all_use_profile' if d.getVar('PYTHON3_PROFILE_OPT', True) == '1' else ''}"
PYTHON3_PROFILE_DIR ?= "${@'${TMPDIR}/work-shared/${MACHINE}/python3/pgo-data' if d.getVar('PYTHON3_PROFILE_OPT', True) == '1' else ''}"
python () {
    if (d.getVar('PYTHON3_PROFILE_OPT', True) == '1' and
            d.getVar('PYTHON3_MAKE_TARGET', True) == 'build_all_use_profile'):
        profile_dir = d.getVar('PYTHON3_PROFILE_DIR', True)
        bb.utils.mkdirhier(profile_dir)
        d.setVarFlag('do_compile', 'file-checksums', '%s:True' % profile_dir)
}

do_configure_append() {
	rm -f ${S}/Makefile.orig
	autoreconf -Wcross --verbose --install --force --exclude=autopoint ../Python-${PV}/Modules/_ctypes/libffi
}

do_compile() {
        # regenerate platform specific files, because they depend on system headers
        cd ${S}/Lib/plat-linux*
        include=${STAGING_INCDIR_DEFAULT} ${STAGING_BINDIR_NATIVE}/python3-native/python3 \
                ${S}/Tools/scripts/h2py.py -i '(u_long)' \
                ${STAGING_INCDIR_DEFAULT}/dlfcn.h \
                ${STAGING_INCDIR_DEFAULT}/linux/cdrom.h \
                ${STAGING_INCDIR_DEFAULT}/netinet/in.h \
                ${STAGING_INCDIR_DEFAULT}/sys/types.h
        sed -e 's,${STAGING_DIR_HOST},,g' -i *.py
        cd -


	# remove any bogus LD_LIBRARY_PATH
	sed -i -e s,RUNSHARED=.*,RUNSHARED=, Makefile

	if [ ! -f Makefile.orig ]; then
		install -m 0644 Makefile Makefile.orig
	fi
	sed -i -e 's,^CONFIGURE_LDFLAGS=.*,CONFIGURE_LDFLAGS=-L. -L${STAGING_LIBDIR_DEFAULT},g' \
		-e 's,libdir=${libdir},libdir=${STAGING_LIBDIR},g' \
		-e 's,libexecdir=${libexecdir},libexecdir=${STAGING_DIR_HOST}${libexecdir},g' \
		-e 's,^LIBDIR=.*,LIBDIR=${STAGING_LIBDIR},g' \
		-e 's,includedir=${includedir},includedir=${STAGING_INCDIR},g' \
		-e 's,^INCLUDEDIR=.*,INCLUDE=${STAGING_INCDIR},g' \
		-e 's,^CONFINCLUDEDIR=.*,CONFINCLUDE=${STAGING_INCDIR},g' \
		Makefile
	# save copy of it now, because if we do it in do_install and 
	# then call do_install twice we get Makefile.orig == Makefile.sysroot
	install -m 0644 Makefile Makefile.sysroot

    if [ "${PYTHON3_MAKE_TARGET}" = "build_all_generate_profile" ]; then
        # This is only used in PGO profiling by python-profile-opt package
        export EXTRA_CFLAGS="-fprofile-dir=./python3-pgo-profiles/"
    elif [ -n "${PYTHON3_PROFILE_DIR}" ]; then
            export EXTRA_CFLAGS="-fprofile-dir=${PYTHON3_PROFILE_DIR}"
            # Remove non-optimized build artefacts
            oe_runmake clean
    fi

	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python3-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python3-native/python3 \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		LIB=${baselib} \
		ARCH=${TARGET_ARCH} \
		OPT="${CFLAGS}" ${PYTHON3_MAKE_TARGET}
}

do_install() {
	# make install needs the original Makefile, or otherwise the inclues would
	# go to ${D}${STAGING...}/...
	install -m 0644 Makefile.orig Makefile

	install -d ${D}${libdir}/pkgconfig
	install -d ${D}${libdir}/python${PYTHON_MAJMIN}/config

    # This only has effect if we build with -fprofile-use, e.g. when make
    # target is build_all_use_profile
    if [ -n "${PYTHON3_PROFILE_DIR}" ]; then
        export EXTRA_CFLAGS="-fprofile-dir=${PYTHON3_PROFILE_DIR}"
    fi
	# rerun the build once again with original makefile this time
	# run install in a separate step to avoid compile/install race
	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python3-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python3-native/python3 \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		LIB=${baselib} \
		ARCH=${TARGET_ARCH} \
		DESTDIR=${D} LIBDIR=${libdir} ${PYTHON3_MAKE_TARGET}
	
    if [ "${PYTHON3_MAKE_TARGET}" = "build_all_generate_profile" ]; then
        # Need special make install if pgo generation is enabled
        _PYTHON3_MAKE_INSTALL_TARGET="install_generate_profile"
    else
        _PYTHON3_MAKE_INSTALL_TARGET="install"
    fi
	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python3-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python3-native/python3 \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		LIB=${baselib} \
		ARCH=${TARGET_ARCH} \
		DESTDIR=${D} LIBDIR=${libdir} ${_PYTHON3_MAKE_INSTALL_TARGET}

	# avoid conflict with 2to3 from Python 2
	rm -f ${D}/${bindir}/2to3

	install -m 0644 Makefile.sysroot ${D}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile
	install -m 0644 Makefile.sysroot ${D}/${libdir}/python${PYTHON_MAJMIN}/config-${PYTHON_MAJMIN}${PYTHON_ABI}/Makefile

	if [ -e ${WORKDIR}/sitecustomize.py ]; then
		install -m 0644 ${WORKDIR}/sitecustomize.py ${D}/${libdir}/python${PYTHON_MAJMIN}
	fi

	oe_multilib_header python${PYTHON_BINABI}/pyconfig.h
}

do_install_append_class-nativesdk () {
	create_wrapper ${D}${bindir}/python${PYTHON_MAJMIN} TERMINFO_DIRS='${sysconfdir}/terminfo:/etc/terminfo:/usr/share/terminfo:/usr/share/misc/terminfo:/lib/terminfo'
}

SSTATE_SCAN_FILES += "Makefile"
PACKAGE_PREPROCESS_FUNCS += "py_package_preprocess"

py_package_preprocess () {
	# copy back the old Makefile to fix target package
	install -m 0644 ${B}/Makefile.orig ${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile
	install -m 0644 ${B}/Makefile.orig ${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config-${PYTHON_MAJMIN}${PYTHON_ABI}/Makefile
	# Remove references to buildmachine paths in target Makefile and _sysconfigdata
	sed -i -e 's:--sysroot=${STAGING_DIR_TARGET}::g' -e s:'--with-libtool-sysroot=${STAGING_DIR_TARGET}'::g \
		${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile \
		${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config-${PYTHON_MAJMIN}${PYTHON_ABI}/Makefile \
		${PKGD}/${libdir}/python${PYTHON_MAJMIN}/_sysconfigdata.py
}

require python-${PYTHON_MAJMIN}-manifest.inc

# manual dependency additions
RPROVIDES_${PN}-modules = "${PN}"
RRECOMMENDS_${PN}-core = "${PN}-readline"
RRECOMMENDS_${PN}-crypt = "openssl"
RRECOMMENDS_${PN}-crypt_class-nativesdk = "nativesdk-openssl"

FILES_${PN}-2to3 += "${bindir}/2to3-${PYTHON_MAJMIN}"
FILES_${PN}-pydoc += "${bindir}/pydoc${PYTHON_MAJMIN} ${bindir}/pydoc3"
FILES_${PN}-idle += "${bindir}/idle3 ${bindir}/idle${PYTHON_MAJMIN}"

PACKAGES =+ "${PN}-pyvenv"
FILES_${PN}-pyvenv += "${bindir}/pyvenv-${PYTHON_MAJMIN} ${bindir}/pyvenv"

# package libpython3
PACKAGES =+ "lib${BPN} lib${BPN}-staticdev"
FILES_lib${BPN} = "${libdir}/libpython*.so.*"
FILES_lib${BPN}-staticdev += "${libdir}/python${PYTHON_MAJMIN}/config-${PYTHON_BINABI}/lib${BPN}*.a"
INSANE_SKIP_${PN}-dev += "dev-elf"

# catch all the rest (unsorted)
PACKAGES += "${PN}-misc"
RDEPENDS_${PN}-misc += "${PN}-core ${PN}-email ${PN}-codecs ${PN}-textutils ${PN}-argparse"
RDEPENDS_${PN}-modules += "${PN}-misc"
FILES_${PN}-misc = "${libdir}/python${PYTHON_MAJMIN}"

# catch manpage
PACKAGES += "${PN}-man"
FILES_${PN}-man = "${datadir}/man"

BBCLASSEXTEND = "nativesdk"
