# These directories will be staged in the sysroot
SYSROOT_DIRS = " \
    ${includedir} \
    ${libdir} \
    ${base_libdir} \
    ${nonarch_base_libdir} \
    ${datadir} \
"

# These directories are also staged in the sysroot when they contain files that
# are usable on the build system
SYSROOT_DIRS_NATIVE = " \
    ${bindir} \
    ${sbindir} \
    ${base_bindir} \
    ${base_sbindir} \
    ${libexecdir} \
    ${sysconfdir} \
    ${localstatedir} \
"
SYSROOT_DIRS_append_class-native = " ${SYSROOT_DIRS_NATIVE}"
SYSROOT_DIRS_append_class-cross = " ${SYSROOT_DIRS_NATIVE}"
SYSROOT_DIRS_append_class-crosssdk = " ${SYSROOT_DIRS_NATIVE}"

# These directories will not be staged in the sysroot
SYSROOT_DIRS_BLACKLIST = " \
    ${mandir} \
    ${docdir} \
    ${infodir} \
    ${datadir}/locale \
    ${datadir}/applications \
    ${datadir}/fonts \
    ${datadir}/pixmaps \
"

sysroot_stage_dir() {
	src="$1"
	dest="$2"
	# if the src doesn't exist don't do anything
	if [ ! -d "$src" ]; then
		 return
	fi

	mkdir -p "$dest"
	(
		cd $src
		find . -print0 | cpio --null -pdlu $dest
	)
}

sysroot_stage_dirs() {
	from="$1"
	to="$2"

	for dir in ${SYSROOT_DIRS}; do
		sysroot_stage_dir "$from$dir" "$to$dir"
	done

	# Remove directories we do not care about
	for dir in ${SYSROOT_DIRS_BLACKLIST}; do
		rm -rf "$to$dir"
	done
}

sysroot_stage_all() {
	sysroot_stage_dirs ${D} ${SYSROOT_DESTDIR}
}

python sysroot_strip () {
    import stat, errno

    dvar = d.getVar('SYSROOT_DESTDIR', True)
    pn = d.getVar('PN', True)

    os.chdir(dvar)

    # Return type (bits):
    # 0 - not elf
    # 1 - ELF
    # 2 - stripped
    # 4 - executable
    # 8 - shared library
    # 16 - kernel module
    def isELF(path):
        type = 0
        ret, result = oe.utils.getstatusoutput("file \"%s\"" % path.replace("\"", "\\\""))

        if ret:
            bb.error("split_and_strip_files: 'file %s' failed" % path)
            return type

        # Not stripped
        if "ELF" in result:
            type |= 1
            if "not stripped" not in result:
                type |= 2
            if "executable" in result:
                type |= 4
            if "shared" in result:
                type |= 8
        return type


    elffiles = {}
    inodes = {}
    libdir = os.path.abspath(dvar + os.sep + d.getVar("libdir", True))
    baselibdir = os.path.abspath(dvar + os.sep + d.getVar("base_libdir", True))
    if (d.getVar('INHIBIT_SYSROOT_STRIP', True) != '1'):
        #
        # First lets figure out all of the files we may have to process
        #
        for root, dirs, files in os.walk(dvar):
            for f in files:
                file = os.path.join(root, f)

                try:
                    ltarget = oe.path.realpath(file, dvar, False)
                    s = os.lstat(ltarget)
                except OSError as e:
                    (err, strerror) = e.args
                    if err != errno.ENOENT:
                        raise
                    # Skip broken symlinks
                    continue
                if not s:
                    continue
                # Check its an excutable
                if (s[stat.ST_MODE] & stat.S_IXUSR) or (s[stat.ST_MODE] & stat.S_IXGRP) or (s[stat.ST_MODE] & stat.S_IXOTH) \
                        or ((file.startswith(libdir) or file.startswith(baselibdir)) and ".so" in f):
                    # If it's a symlink, and points to an ELF file, we capture the readlink target
                    if os.path.islink(file):
                        continue

                    # It's a file (or hardlink), not a link
                    # ...but is it ELF, and is it already stripped?
                    elf_file = isELF(file)
                    if elf_file & 1:
                        if elf_file & 2:
                            if 'already-stripped' in (d.getVar('INSANE_SKIP_' + pn, True) or "").split():
                                bb.note("Skipping file %s from %s for already-stripped QA test" % (file[len(dvar):], pn))
                            else:
                                bb.warn("File '%s' from %s was already stripped, this will prevent future debugging!" % (file[len(dvar):], pn))
                            continue

                        if s.st_ino in inodes:
                            os.unlink(file)
                            os.link(inodes[s.st_ino], file)
                        else:
                            inodes[s.st_ino] = file
                            # break hardlink
                            bb.utils.copyfile(file, file)
                            elffiles[file] = elf_file

        #
        # Now strip them (in parallel)
        #
        strip = d.getVar("STRIP", True)
        sfiles = []
        for file in elffiles:
            elf_file = int(elffiles[file])
            #bb.note("Strip %s" % file)
            sfiles.append((file, elf_file, strip))

        oe.utils.multiprocess_exec(sfiles, oe.package.runstrip)
}

do_populate_sysroot[dirs] = "${SYSROOT_DESTDIR}"
do_populate_sysroot[umask] = "022"

addtask populate_sysroot after do_install

SYSROOT_PREPROCESS_FUNCS ?= ""
SYSROOT_DESTDIR = "${WORKDIR}/sysroot-destdir"

# We clean out any existing sstate from the sysroot if we rerun configure
python sysroot_cleansstate () {
    ss = sstate_state_fromvars(d, "populate_sysroot")
    sstate_clean(ss, d)
}
do_configure[prefuncs] += "sysroot_cleansstate"


BB_SETSCENE_VERIFY_FUNCTION2 = "sysroot_checkhashes2"

def sysroot_checkhashes2(covered, tasknames, fns, d, invalidtasks):
    problems = set()
    configurefns = set()
    for tid in invalidtasks:
        if tasknames[tid] == "do_configure" and tid not in covered:
            configurefns.add(fns[tid])
    for tid in covered:
        if tasknames[tid] == "do_populate_sysroot" and fns[tid] in configurefns:
            problems.add(tid)
    return problems

BB_SETSCENE_VERIFY_FUNCTION = "sysroot_checkhashes"

def sysroot_checkhashes(covered, tasknames, fnids, fns, d, invalidtasks = None):
    problems = set()
    configurefnids = set()
    if not invalidtasks:
        invalidtasks = range(len(tasknames))
    for task in invalidtasks:
        if tasknames[task] == "do_configure" and task not in covered:
            configurefnids.add(fnids[task])
    for task in covered:
        if tasknames[task] == "do_populate_sysroot" and fnids[task] in configurefnids:
            problems.add(task)
    return problems

python do_populate_sysroot () {
    bb.build.exec_func("sysroot_stage_all", d)
    bb.build.exec_func("sysroot_strip", d)
    for f in (d.getVar('SYSROOT_PREPROCESS_FUNCS', True) or '').split():
        bb.build.exec_func(f, d)
    pn = d.getVar("PN", True)
    multiprov = d.getVar("MULTI_PROVIDER_WHITELIST", True).split()
    provdir = d.expand("${SYSROOT_DESTDIR}${base_prefix}/sysroot-providers/")
    bb.utils.mkdirhier(provdir)
    for p in d.getVar("PROVIDES", True).split():
        if p in multiprov:
            continue
        p = p.replace("/", "_")
        with open(provdir + p, "w") as f:
            f.write(pn)
}

do_populate_sysroot[vardeps] += "${SYSROOT_PREPROCESS_FUNCS}"
do_populate_sysroot[vardepsexclude] += "MULTI_PROVIDER_WHITELIST"

SSTATETASKS += "do_populate_sysroot"
do_populate_sysroot[cleandirs] = "${SYSROOT_DESTDIR}"
do_populate_sysroot[sstate-inputdirs] = "${SYSROOT_DESTDIR}"
do_populate_sysroot[sstate-outputdirs] = "${STAGING_DIR}/${PACKAGE_ARCH}/${PN}"
do_populate_sysroot[sstate-fixmedir] = "${STAGING_DIR}/${PACKAGE_ARCH}/${PN}"

python do_populate_sysroot_setscene () {
    sstate_setscene(d)
}
addtask do_populate_sysroot_setscene

#
# Manifests here are complicated. The main sysroot area has the unpacked sstate
# which us unrelocated and tracked by the main sstate manifests. Each recipe
# specific sysroot has manifests for each dependency that is installed there.
# The task hash is used to tell whether the data needs to be reinstalled. We 
# use a symlink to point to the currently installed hash. There is also a 
# "complete" stamp file which is used to mark if installation completed. If 
# something fails (e.g. a postinst), this won't get written and we would 
# remove and reinstall the dependency. This also means partially installed 
# dependencies should get cleaned up correctly.
#

python extend_recipe_sysroot() {
    import copy
    import subprocess

    taskdepdata = d.getVar("BB_TASKDEPDATA", False)
    mytaskname = d.getVar("BB_RUNTASK", True)
    #bb.warn(str(taskdepdata))
    pn = d.getVar("PN", True)

    if mytaskname.endswith("_setscene"):
        mytaskname = mytaskname.replace("_setscene", "")

    start = None
    configuredeps = []
    # Detect bitbake -b usage
    # Everything but quilt-native would have dependencies
    nodeps = (pn != "quilt-native")

    for dep in taskdepdata:
        data = taskdepdata[dep]
        if data[1] == mytaskname and data[0] == pn:
            start = dep
        if not nodeps and start:
            break
        if nodeps and data[0] != pn:
            nodeps = False
    if start is None:
        bb.fatal("Couldn't find ourself in BB_TASKDEPDATA?")

    # We need to figure out which sysroot files we need to expose to this task.
    # This needs to match what would get restored from sstate, which is controlled 
    # ultimately by calls from bitbake to setscene_depvalid().
    # That function expects a setscene dependency tree. We build a dependency tree 
    # condensed to inter-sstate task dependencies, similar to that used by setscene 
    # tasks. We can then call into setscene_depvalid() and decide
    # which dependencies we can "see" and should expose in the recipe specific sysroot.
    setscenedeps = copy.deepcopy(taskdepdata)

    start = set([start])

    sstatetasks = d.getVar("SSTATETASKS", True).split()

    def print_dep_tree(deptree):
        data = ""
        for dep in deptree:
            deps = "    " + "\n    ".join(deptree[dep][3]) + "\n"
            data = "%s:\n  %s\n  %s\n%s  %s\n  %s\n" % (deptree[dep][0], deptree[dep][1], deptree[dep][2], deps, deptree[dep][4], deptree[dep][5])
        return data

    #bb.note("Full dep tree is:\n%s" % print_dep_tree(taskdepdata))

    #bb.note(" start2 is %s" % str(start))

    # If start is an sstate task (like do_package) we need to add in its direct dependencies
    # else the code below won't recurse into them.
    for dep in set(start):
        for dep2 in setscenedeps[dep][3]:
            start.add(dep2)
        start.remove(dep)

    #bb.note(" start3 is %s" % str(start))

    # Create collapsed do_populate_sysroot -> do_populate_sysroot tree
    for dep in taskdepdata:
        data = setscenedeps[dep]        
        if data[1] not in sstatetasks:
            for dep2 in setscenedeps:
                data2 = setscenedeps[dep2]
                if dep in data2[3]:
                    data2[3].update(setscenedeps[dep][3])
                    data2[3].remove(dep)
            if dep in start:
                start.update(setscenedeps[dep][3])
                start.remove(dep)
            del setscenedeps[dep]

    # Remove circular references
    for dep in setscenedeps:
        if dep in setscenedeps[dep][3]:
            setscenedeps[dep][3].remove(dep)

    #bb.note("Computed dep tree is:\n%s" % print_dep_tree(setscenedeps))
    #bb.note(" start is %s" % str(start))

    # Direct dependencies should be present and can be depended upon
    for dep in set(start):
        if setscenedeps[dep][1] == "do_populate_sysroot":
            if dep not in configuredeps:
                configuredeps.append(dep)
    bb.note("Direct dependencies are %s" % str(configuredeps))
    #bb.note(" or %s" % str(start))

    # Call into setscene_depvalid for each sub-dependency and only copy sysroot files
    # for ones that would be restored from sstate.
    done = list(start)
    next = list(start)
    while next:
        new = []
        for dep in next:
            data = setscenedeps[dep]
            for datadep in data[3]:
                if datadep in done:
                    continue
                taskdeps = {}
                taskdeps[dep] = setscenedeps[dep][:2]
                taskdeps[datadep] = setscenedeps[datadep][:2]
                retval = setscene_depvalid(datadep, taskdeps, [], d)
                if retval:
                    bb.note("Skipping setscene dependency %s for installation into the sysroot" % datadep)
                    continue
                done.append(datadep)
                new.append(datadep)
                if datadep not in configuredeps and setscenedeps[datadep][1] == "do_populate_sysroot":
                    configuredeps.append(datadep)
                    bb.note("Adding dependency on %s" % setscenedeps[datadep][0])
                else:
                    bb.note("Following dependency on %s" % setscenedeps[datadep][0])
        next = new

    #if nodeps:
        # FIXME
        #bb.warn("Unable to find task dependencies, -b being used? Pulling in all m4 files")
        #for l in [d.expand("${STAGING_DATADIR_NATIVE}/aclocal/"), d.expand("${STAGING_DATADIR}/aclocal/")]:
        #    cp.extend(os.path.join(l, f) for f in os.listdir(l))

    stagingdir = d.getVar("STAGING_DIR", True)
    recipesysroot = d.getVar("RECIPE_SYSROOT", True)
    recipesysrootnative = d.getVar("RECIPE_SYSROOT_NATIVE", True)

    depdir = recipesysroot + "/installeddeps"
    bb.utils.mkdirhier(depdir)

    lock = bb.utils.lockfile(recipesysroot + "/sysroot.lock")

    fixme = []
    fixmenative = []
    postinsts = []

    def copyfile(c, target, fixme):
        if c.endswith("/fixmepath"):
            fixme.append(c)
            return None
        if c.endswith("/fixmepath.cmd"):
            return None
        #bb.warn(c)
        dest = c.replace(stagingdir, "")
        dest = target + "/" + "/".join(dest.split("/")[3:])
        bb.utils.mkdirhier(os.path.dirname(dest))
        if "/usr/bin/postinst-" in c:
            postinsts.append(dest)
        if os.path.islink(c):
            linkto = os.readlink(c)
            if os.path.lexists(dest):
                if os.readlink(dest) == linkto:
                    return dest
                bb.fatal("Link %s already exists to a different location?" % dest)
            os.symlink(linkto, dest)
            #bb.warn(c)
        else:
            os.link(c, dest)
        return dest

    for dep in configuredeps:
        c = setscenedeps[dep][0]
        taskhash = setscenedeps[dep][5]
        taskmanifest = depdir + "/" + c + "." + taskhash
        if os.path.exists(depdir + "/" + c):
            lnk = os.readlink(depdir + "/" + c)
            if lnk == c + "." + taskhash and os.path.exists(depdir + "/" + c + ".complete"): 
                bb.note("%s exists in sysroot, skipping" % c)
                continue
            else:
                bb.note("%s exists in sysroot, but is stale (%s vs. %s), removing." % (c, lnk, c + "." + taskhash))
                sstate_clean_manifest(depdir + "/" + lnk, d)
                os.unlink(depdir + "/" + c)
        elif os.path.lexists(depdir + "/" + c):
            os.unlink(depdir + "/" + c)

        os.symlink(c + "." + taskhash, depdir + "/" + c)

        native = False
        if c.endswith("-native"):
            manifest = d.expand("${SSTATE_MANIFESTS}/manifest-${BUILD_ARCH}-%s.populate_sysroot" % c)
            native = True
        elif c.startswith("nativesdk-"):
            manifest = d.expand("${SSTATE_MANIFESTS}/manifest-${SDK_ARCH}_${SDK_OS}-%s.populate_sysroot" % c)
        elif "-cross-" in c:
            manifest = d.expand("${SSTATE_MANIFESTS}/manifest-${BUILD_ARCH}_${TARGET_ARCH}-%s.populate_sysroot" % c)
            native = True
        elif "-crosssdk" in c:
            manifest = d.expand("${SSTATE_MANIFESTS}/manifest-${BUILD_SDK}_${SDK_ARCH}_${SDK_OS}-%s.populate_sysroot" % c)
            native = True
        else:
            manifest = d.expand("${SSTATE_MANIFESTS}/manifest-${MACHINE_ARCH}-%s.populate_sysroot" % c)
            if not os.path.exists(manifest):
                manifest = d.expand("${SSTATE_MANIFESTS}/manifest-${TUNE_PKGARCH}-%s.populate_sysroot" % c)
            if not os.path.exists(manifest):
                manifest = d.expand("${SSTATE_MANIFESTS}/manifest-allarch-%s.populate_sysroot" % c)
        if not os.path.exists(manifest):
            bb.warn("Manifest %s not found?" % manifest)
        else:
            with open(manifest, "r") as f, open(taskmanifest, 'w') as m: 
                for l in f:
                    l = l.strip()
                    if l.endswith("/"):
                        continue
                    if native:
                        dest = copyfile(l, recipesysrootnative, fixmenative)
                    else:
                        dest = copyfile(l, recipesysroot, fixme)
                    if dest:
                        m.write(dest + "\n")

    def processfixme(fixme, target):
        if not fixme:
            return
        cmd = "sed -e 's:^[^/]*/:%s/:g' %s | xargs sed -i -e 's:FIXMESTAGINGDIRTARGET:%s:g; s:FIXMESTAGINGDIRHOST:%s:g'" % (target, " ".join(fixme), recipesysroot, recipesysrootnative)
        bb.note(cmd)
        subprocess.check_call(cmd, shell=True)

    processfixme(fixme, recipesysroot)
    processfixme(fixmenative, recipesysrootnative)

    for p in postinsts:
        subprocess.check_call(p, shell=True)

    for dep in configuredeps:
        c = setscenedeps[dep][0]
        open(depdir + "/" + c + ".complete", "w").close()

    bb.utils.unlockfile(lock)
}
extend_recipe_sysroot[vardepsexclude] += "MACHINE SDK_ARCH BUILD_ARCH SDK_OS BB_TASKDEPDATA"

python do_prepare_recipe_sysroot () {
    bb.build.exec_func("extend_recipe_sysroot", d)
}
addtask do_prepare_recipe_sysroot before do_configure after do_fetch

# Clean out the recipe specific sysroots before do_fetch
do_fetch[cleandirs] += "${RECIPE_SYSROOT} ${RECIPE_SYSROOT_NATIVE}"

python () {
    bbtasks = d.getVar('__BBTASKS', False) or []
    for task in bbtasks:
        deps = d.getVarFlag(task, "depends", True)
        if deps and "populate_sysroot" in deps or task == "do_package" or task.startswith("do_package_write_"):
            d.appendVarFlag(task, "prefuncs", " extend_recipe_sysroot")
}


