# Instructions to make this work:
#
# bitbake <targets>
# bitbake <targets> -S none
# bitbake meta-newsdk
#
# This will produce an installable SDK containing a copy of the build
# system whose output is locked at the point it was generated.
#

SUMMARY = "New SDK"
LICENSE = "MIT"

LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690 \
                    file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

inherit populate_sdk

SRC_URI = "file://sdktool.py"

# Parts of this borrowed from buildtools-tarball
TOOLCHAIN_HOST_TASK = " \
    nativesdk-qemu \
    nativesdk-qemu-helper \
    nativesdk-pseudo \
    \
    nativesdk-python-core \
    nativesdk-python-textutils \
    nativesdk-python-sqlite3 \
    nativesdk-python-pickle \
    nativesdk-python-logging \
    nativesdk-python-elementtree \
    nativesdk-python-curses \
    nativesdk-python-compile \
    nativesdk-python-compiler \
    nativesdk-python-fcntl \
    nativesdk-python-shell \
    nativesdk-python-misc \
    nativesdk-python-multiprocessing \
    nativesdk-python-subprocess \
    nativesdk-python-xmlrpc \
    nativesdk-python-netclient \
    nativesdk-python-netserver \
    nativesdk-python-distutils \
    nativesdk-python-unixadmin \
    nativesdk-python-compression \
    nativesdk-python-json \
    nativesdk-python-unittest \
    nativesdk-python-mmap \
    nativesdk-python-difflib \
    nativesdk-python-pprint \
    nativesdk-python-git \
    nativesdk-python-pkgutil \
    nativesdk-ncurses-terminfo-base \
    nativesdk-chrpath \
    nativesdk-tar \
    nativesdk-git \
    nativesdk-pigz \
    nativesdk-make \
    nativesdk-wget \
    "

TOOLCHAIN_TARGET_TASK = ""

SDK_META_CONF_WHITELIST ?= "MACHINE DISTRO PACKAGE_CLASSES"

SDK_TARGETS ?= "core-image-minimal"


python copy_buildsystem () {
    import re

    layerdirs = d.getVar('BBLAYERS', True).split()

    res = re.search('([^:]*/bitbake/bin)', d.getVar('PATH', True))
    if res:
        bitbakepath = os.path.dirname(res.group(1))
        layerdirs.append(bitbakepath)
    else:
        bb.fatal('Unable to find bitbake path!')

    # Copy in all metadata layers + bitbake (as repositories)
    baseoutpath = d.getVar('SDK_OUTPUT', True) + '/' + d.getVar('SDKPATH', True)
    bb.utils.mkdirhier(baseoutpath + '/layers')
    sdkbblayers = []
    for layer in layerdirs:
        stdout, stderr = bb.process.run("git rev-parse --show-toplevel", cwd=layer)
        toplevel = stdout.strip()
        sdkdestpath = baseoutpath + '/layers/' + os.path.basename(toplevel)
        if os.path.exists(sdkdestpath):
            bb.note("Skipping layer %s, already handled" % layer)
        else:
            bb.note("Cloning %s for %s..." % (toplevel, layer))
            bb.process.run("git clone %s %s" % (toplevel, sdkdestpath))
            bb.process.run("git gc --auto", cwd=sdkdestpath)

        if layer != bitbakepath:
            layerrelpath = os.path.relpath(layer, os.path.dirname(toplevel))
            sdkbblayers.append(layerrelpath)
        else:
	    d.setVar('BITBAKE_LAYER', os.path.basename(toplevel))

    # Create a layer for new recipes / appends
    bb.utils.mkdirhier(baseoutpath + '/workspace/conf')
    with open(baseoutpath + '/workspace/conf/layer.conf', 'w') as f:
        f.write('BBPATH =. "$' + '{LAYERDIR}:"\n')
        f.write('BBFILES += "$' + '{LAYERDIR}/*/*.bb \\\n')
        f.write('            $' + '{LAYERDIR}/*/*.bbappend"\n')
        f.write('BBFILE_COLLECTIONS += "sdkworkspace"\n')
        f.write('BBFILE_PATTERN_sdkworkspace = "^$' + '{LAYERDIR}/"\n')
        f.write('BBFILE_PRIORITY_sdkworkspace = "99"\n')

    # Create bblayers.conf
    bb.utils.mkdirhier(baseoutpath + '/conf')
    with open(baseoutpath + '/conf/bblayers.conf', 'w') as f:
        f.write('LCONF_VERSION = "%s"\n\n' % d.getVar('LCONF_VERSION'))
        f.write('BBPATH = "$' + '{TOPDIR}"\n')
        f.write('SDKBASEMETAPATH = "$' + '{TOPDIR}"\n')
        f.write('BBLAYERS := " \\\n')
        for layerrelpath in sdkbblayers:
            f.write('    $' + '{SDKBASEMETAPATH}/layers/%s \\\n' % layerrelpath)
        f.write('    $' + '{SDKBASEMETAPATH}/workspace \\\n')
        f.write('    "\n')

    # Create local.conf
    with open(baseoutpath + '/conf/local.conf', 'w') as f:
        f.write('CONF_VERSION = "%s"\n\n' % d.getVar('CONF_VERSION'))

        # This is a bit of a hack, but we really don't want these dependencies
        # (we're including them in the SDK as nativesdk- versions instead)
        f.write('POKYQEMUDEPS_forcevariable = ""\n\n')

        # Another hack, but we want the native part of sstate to be kept the same
        # regardless of the host distro
        fixedlsbstring = 'SDK-Fixed'
        f.write('NATIVELSBSTRING_forcevariable = "%s"\n\n' % fixedlsbstring)

        for varname in d.getVar('SDK_META_CONF_WHITELIST', True).split():
            f.write('%s = "%s"\n' % (varname, d.getVar(varname, True)))
        f.write('\nINHERIT += "externalsrc"\n\n')
        f.write('require conf/locked-sigs.inc\n')
        f.write('require conf/work-config.inc\n')

    # Filter the locked signatures file to just the sstate tasks we are interested in
    allowed_tasks = ['do_populate_lic', 'do_populate_sysroot', 'do_packagedata', 'do_package_write', 'do_package_qa', 'do_deploy']
    inputfile = d.getVar('TOPDIR', True) + '/locked-sigs.inc'
    with open(inputfile, 'r') as infile:
        with open(baseoutpath + '/conf/locked-sigs.inc', 'w') as f:
            invalue = False
            for line in infile:
                if invalue:
                    if line.endswith('\\\n'):
                        for task in allowed_tasks:
                            if task in line:
                                f.write(line)
                                break
                    else:
                        f.write(line)
                        break
                elif line.startswith('SIGGEN_LOCKEDSIGS'):
                    invalue = True
                    f.write(line)

    # Create a dummy config file for additional settings
    with open(baseoutpath + '/conf/work-config.inc', 'w') as f:
        pass

    # Create a filtered sstate cache containing only the items in the filtered list
    bb.utils.mkdirhier(baseoutpath + '/sstate-cache')
    bb.note('Generating sstate-cache...')
    bb.process.run("gen-lockedsig-cache %s %s %s" % (baseoutpath + '/conf/locked-sigs.inc', d.getVar('SSTATE_DIR', True), baseoutpath + '/sstate-cache'))
    os.rename(baseoutpath + '/sstate-cache/' + d.getVar('NATIVELSBSTRING', True), baseoutpath + '/sstate-cache/' + fixedlsbstring)
}

install_sdktool() {
	install -d ${SDK_OUTPUT}/${SDKPATHNATIVE}${bindir_nativesdk}
	install -m 0755 ${WORKDIR}/sdktool.py ${SDK_OUTPUT}/${SDKPATHNATIVE}${bindir_nativesdk}/sdktool
	sed -i -e 's#BITBAKE_LAYER#${BITBAKE_LAYER}#g' ${SDK_OUTPUT}/${SDKPATHNATIVE}${bindir_nativesdk}/sdktool
}

toolchain_create_sdk_env_script () {
	# Create minimal environment setup script
	script=${SDK_OUTPUT}/${SDKPATH}/environment-setup-newsdk
	rm -f $script
	touch $script
	EXTRAPATH=""
	for i in ${CANADIANEXTRAOS}; do
		EXTRAPATH="$EXTRAPATH:${SDKPATHNATIVE}${bindir_nativesdk}/${TARGET_ARCH}${TARGET_VENDOR}-$i"
	done
	echo 'export PATH=${SDKPATHNATIVE}${bindir_nativesdk}:${SDKPATHNATIVE}${bindir_nativesdk}/${TARGET_SYS}'$EXTRAPATH':$PATH' >> $script
	echo 'export OECORE_NATIVE_SYSROOT="${SDKPATHNATIVE}"' >> $script
	echo 'export PYTHONHOME=${SDKPATHNATIVE}${prefix_nativesdk}' >> $script
}

# FIXME this preparation should be done as part of the SDK construction
sdk_postinst() {
	echo done
	printf "Preparing build system..."
	cd $target_sdk_dir
	sh -c ". layers/${BITBAKE_LAYER}/oe-init-build-env . > /dev/null && bitbake ${SDK_TARGETS} > /dev/null"
}

SDK_POST_INSTALL_COMMAND = "${sdk_postinst}"

SDK_POSTPROCESS_COMMAND_prepend = "copy_buildsystem; install_sdktool; "
