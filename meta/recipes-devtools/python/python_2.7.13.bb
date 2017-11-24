require python.inc

DEPENDS = "python-native libffi bzip2 gdbm openssl readline sqlite3 zlib"

PR = "${INC_PR}"

DISTRO_SRC_URI ?= "file://sitecustomize.py"
DISTRO_SRC_URI_linuxstdbase = ""
SRC_URI += "\
  file://01-use-proper-tools-for-cross-build.patch \
  file://03-fix-tkinter-detection.patch \
  file://06-avoid_usr_lib_termcap_path_in_linking.patch \
  ${DISTRO_SRC_URI} \
  file://multilib.patch \
  file://cgi_py.patch \
  file://setup_py_skip_cross_import_check.patch \
  file://add-md5module-support.patch \
  file://host_include_contamination.patch \
  file://fix_for_using_different_libdir.patch \
  file://setuptweaks.patch \
  file://check-if-target-is-64b-not-host.patch \
  file://search_db_h_in_inc_dirs_and_avoid_warning.patch \
  file://avoid_warning_about_tkinter.patch \
  file://avoid_warning_for_sunos_specific_module.patch \
  file://python-2.7.3-remove-bsdb-rpath.patch \
  file://fix-makefile-for-ptest.patch \
  file://run-ptest \
  file://parallel-makeinst-create-bindir.patch \
  file://use_sysroot_ncurses_instead_of_host.patch \
  file://add-CROSSPYTHONPATH-for-PYTHON_FOR_BUILD.patch \
  file://Don-t-use-getentropy-on-Linux.patch \
  file://pass-missing-libraries-to-Extension-for-mul.patch \
  file://support_SOURCE_DATE_EPOCH_in_py_compile_2.7.patch \
"

S = "${WORKDIR}/Python-${PV}"

inherit autotools multilib_header python-dir pythonnative

CONFIGUREOPTS += " --with-system-ffi "

EXTRA_OECONF += "ac_cv_file__dev_ptmx=yes ac_cv_file__dev_ptc=no"

PACKAGECONFIG ??= "bdb"
PACKAGECONFIG[bdb] = ",,db"

do_configure_append() {
	rm -f ${S}/Makefile.orig
        autoreconf -Wcross --verbose --install --force --exclude=autopoint ../Python-${PV}/Modules/_ctypes/libffi
}

do_compile() {
        # regenerate platform specific files, because they depend on system headers
        cd ${S}/Lib/plat-linux2
        include=${STAGING_INCDIR} ${STAGING_BINDIR_NATIVE}/python-native/python \
                ${S}/Tools/scripts/h2py.py -i '(u_long)' \
                ${STAGING_INCDIR}/dlfcn.h \
                ${STAGING_INCDIR}/linux/cdrom.h \
                ${STAGING_INCDIR}/netinet/in.h \
                ${STAGING_INCDIR}/sys/types.h
        sed -e 's,${STAGING_DIR_HOST},,g' -i *.py
        cd -

	# remove any bogus LD_LIBRARY_PATH
	sed -i -e s,RUNSHARED=.*,RUNSHARED=, Makefile

	if [ ! -f Makefile.orig ]; then
		install -m 0644 Makefile Makefile.orig
	fi
	sed -i -e 's#^LDFLAGS=.*#LDFLAGS=${LDFLAGS} -L. -L${STAGING_LIBDIR}#g' \
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

	export CROSS_COMPILE="${TARGET_PREFIX}"
	export PYTHONBUILDDIR="${B}"

	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python-native/python \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		OPT="${CFLAGS}"
}

do_install() {
	# make install needs the original Makefile, or otherwise the inclues would
	# go to ${D}${STAGING...}/...
	install -m 0644 Makefile.orig Makefile

	export CROSS_COMPILE="${TARGET_PREFIX}"
	export PYTHONBUILDDIR="${B}"

	# After swizzling the makefile, we need to run the build again.
	# install can race with the build so we have to run this first, then install
	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python-native/python \
		CROSSPYTHONPATH=${STAGING_LIBDIR_NATIVE}/python${PYTHON_MAJMIN}/lib-dynload/ \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		DESTDIR=${D} LIBDIR=${libdir}
	
	oe_runmake HOSTPGEN=${STAGING_BINDIR_NATIVE}/python-native/pgen \
		HOSTPYTHON=${STAGING_BINDIR_NATIVE}/python-native/python \
		CROSSPYTHONPATH=${STAGING_LIBDIR_NATIVE}/python${PYTHON_MAJMIN}/lib-dynload/ \
		STAGING_LIBDIR=${STAGING_LIBDIR} \
		STAGING_INCDIR=${STAGING_INCDIR} \
		STAGING_BASELIBDIR=${STAGING_BASELIBDIR} \
		DESTDIR=${D} LIBDIR=${libdir} install

	install -m 0644 Makefile.sysroot ${D}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile

	if [ -e ${WORKDIR}/sitecustomize.py ]; then
		install -m 0644 ${WORKDIR}/sitecustomize.py ${D}/${libdir}/python${PYTHON_MAJMIN}
	fi

	oe_multilib_header python${PYTHON_MAJMIN}/pyconfig.h

    if [ -z "${@bb.utils.filter('PACKAGECONFIG', 'bdb', d)}" ]; then
        rm -rf ${D}/${libdir}/python${PYTHON_MAJMIN}/bsddb
    fi
}

do_install_append_class-nativesdk () {
	create_wrapper ${D}${bindir}/python2.7 PYTHONHOME='${prefix}' TERMINFO_DIRS='${sysconfdir}/terminfo:/etc/terminfo:/usr/share/terminfo:/usr/share/misc/terminfo:/lib/terminfo'
}

SSTATE_SCAN_FILES += "Makefile"
PACKAGE_PREPROCESS_FUNCS += "py_package_preprocess"

py_package_preprocess () {
	# copy back the old Makefile to fix target package
	install -m 0644 ${B}/Makefile.orig ${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile

	# Remove references to buildmachine paths in target Makefile and _sysconfigdata
	sed -i -e 's:--sysroot=${STAGING_DIR_TARGET}::g' -e s:'--with-libtool-sysroot=${STAGING_DIR_TARGET}'::g \
		${PKGD}/${libdir}/python${PYTHON_MAJMIN}/config/Makefile \
		${PKGD}/${libdir}/python${PYTHON_MAJMIN}/_sysconfigdata.py
    python -m py_compile ${PKGD}/${libdir}/python${PYTHON_MAJMIN}/_sysconfigdata.py
}


# manual dependency additions
RPROVIDES_${PN}-core = "${PN}"
RRECOMMENDS_${PN}-core_append_class-nativesdk = " nativesdk-python-modules"
RRECOMMENDS_${PN}-crypt = "openssl"

# package libpython2
PACKAGES =+ "lib${BPN}2"
FILES_lib${BPN}2 = "${libdir}/libpython*.so.*"

# catch all the rest (unsorted)
PACKAGES += "${PN}-misc"
FILES_${PN}-misc = "${libdir}/python${PYTHON_MAJMIN}"
RDEPENDS_${PN}-modules += "${PN}-misc"

# ptest
RDEPENDS_${PN}-ptest = "${PN}-modules ${PN}-tests"
#inherit ptest after "require python-${PYTHON_MAJMIN}-manifest.inc" so PACKAGES doesn't get overwritten
inherit ptest

# This must come after inherit ptest for the override to take effect
do_install_ptest() {
	cp ${B}/Makefile ${D}${PTEST_PATH}
	sed -e s:LIBDIR/python/ptest:${PTEST_PATH}:g \
	 -e s:LIBDIR:${libdir}:g \
	 -i ${D}${PTEST_PATH}/run-ptest

	#Remove build host references
	sed -i \
		-e 's:--with-libtool-sysroot=${STAGING_DIR_TARGET}'::g \
	    -e 's:--sysroot=${STAGING_DIR_TARGET}::g' \
	    -e 's|${DEBUG_PREFIX_MAP}||g' \
	    -e 's:${HOSTTOOLS_DIR}/::g' \
	    -e 's:${RECIPE_SYSROOT}::g' \
	    -e 's:${BASE_WORKDIR}/${MULTIMACH_TARGET_SYS}::g' \
	${D}/${PTEST_PATH}/Makefile
}

# catch manpage
PACKAGES += "${PN}-man"
FILES_${PN}-man = "${datadir}/man"

# Nasty but if bdb isn't enabled the package won't be generated
RDEPENDS_${PN}-modules_remove = "${@bb.utils.contains('PACKAGECONFIG', 'bdb', '', '${PN}-bsddb', d)}"

BBCLASSEXTEND = "nativesdk"

RPROVIDES_${PN} += "${PN}-modules"

# We want bytecode precompiled .py files (.pyc's) by default
# but the user may set it on their own conf

INCLUDE_PYCS ?= "1"

python(){

    pythondir = d.getVar('THISDIR',True)

    # Read JSON manifest
    import json
    with open(pythondir+'/python/python2-manifest.json') as manifest_file:
        python_manifest=json.load(manifest_file)

    include_pycs = d.getVar('INCLUDE_PYCS')

    packages = d.getVar('PACKAGES').split()
    pn = d.getVar('PN')


    newpackages=[]

    for key in python_manifest:
        pypackage= pn + '-' + key

        if pypackage not in packages:
            # We need to prepend, otherwise python-misc gets everything
            # so we use a new variable
            newpackages.append(pypackage)

        # "Build" python's manifest FILES, RDEPENDS and SUMMARY
        d.setVar('FILES_' + pypackage, '')
        for value in python_manifest[key]['files']:
            d.appendVar('FILES_' + pypackage, value + ' ')
            if include_pycs == '1':
                if value.endswith('.py'):
                    d.appendVar('FILES_' + pypackage, value + 'c ')

        d.setVar('RDEPENDS_' + pypackage, '')
        for value in python_manifest[key]['rdepends']:
            # Make it work with or without $PN
            if '${PN}' in value:
                value=value.split('-')[1]
            d.appendVar('RDEPENDS_' + pypackage, pn + '-' + value + ' ')
        d.setVar('SUMMARY_' + pypackage, python_manifest[key]['summary'])

    # We need to ensure staticdev packages match for files first so we sort in reverse
    newpackages.sort(reverse=True)
    # Prepending so to avoid python-misc getting everything
    packages = newpackages + packages
    d.setVar('PACKAGES', ' '.join(packages))
    d.setVar('ALLOW_EMPTY_${PN}-modules', '1')
}

do_split_packages[file-checksums] += "${THISDIR}/python/python2-manifest.json:True"

# Files needed to create a new manifest
SRC_URI += "file://create_manifest2.py file://get_module_deps2.py file://python2-manifest.json"

do_create_manifest() {

cd ${WORKDIR}
# This needs to be executed by python-native and NOT by HOST's python
nativepython create_manifest2.py
cp python2-manifest.json.new ${THISDIR}/python/python2-manifest.json
}

# bitbake python -c create_manifest
addtask do_create_manifest

# Make sure we have native python ready when we create a new manifest
do_create_manifest[depends] += "python:do_prepare_recipe_sysroot"
do_create_manifest[depends] += "python:do_patch"
