# Class to avoid copying packages into the feed if they haven't materially changed
#
# Copyright (C) 2015 Intel Corporation
# Released under the MIT license (see COPYING.MIT for details)
#
# This class effectively intercepts packages as they are written out by
# do_package_write_*, causing them to be written into a different
# directory where we can compare them to whatever older packages might
# be in the "real" package feed directory, and avoid copying the new
# package to the feed if it has not materially changed. The idea is to
# avoid unnecessary churn in the packages when dependencies trigger task
# reexecution (and thus repackaging). Enabling the class is simple:
#
# INHERIT += "packagefeed-stability"
#
# Caveats:
# 1) Latest PR values in the build system may not match those in packages
#    seen on the target (naturally)
# 2) If you rebuild from sstate without the existing package feed present,
#    you will lose the "state" of the package feed i.e. the preserved old
#    package versions. Not the end of the world, but would negate the
#    entire purpose of this class.
#
# Note that running -c cleanall on a recipe will purposely delete the old
# package files so they will definitely be copied the next time.

python() {
    # Package backend agnostic intercept
    # This assumes that the package_write task is called package_write_<pkgtype>
    # and that the directory in which packages should be written is
    # pointed to by the variable DEPLOY_DIR_<PKGTYPE>
    for pkgclass in (d.getVar('PACKAGE_CLASSES', True) or '').split():
        if pkgclass.startswith('package_'):
            pkgtype = pkgclass.split('_', 1)[1]
            pkgwritefunc = 'do_package_write_%s' % pkgtype
            sstate_outputdirs = d.getVarFlag(pkgwritefunc, 'sstate-outputdirs', False)
            deploydirvar = 'DEPLOY_DIR_%s' % pkgtype.upper()
            deploydirvarref = '${' + deploydirvar + '}'
            pkgcomparefunc = 'do_package_compare_%s' % pkgtype

            if bb.data.inherits_class('image', d):
                d.appendVarFlag('do_rootfs', 'recrdeptask', ' ' + pkgcomparefunc)

            if bb.data.inherits_class('populate_sdk_base', d):
                d.appendVarFlag('do_populate_sdk', 'recrdeptask', ' ' + pkgcomparefunc)

            if bb.data.inherits_class('populate_sdk_ext', d):
                d.appendVarFlag('do_populate_sdk_ext', 'recrdeptask', ' ' + pkgcomparefunc)

            d.appendVarFlag('do_build', 'recrdeptask', ' ' + pkgcomparefunc)

            if d.getVarFlag(pkgwritefunc, 'noexec', True) or (not d.getVarFlag(pkgwritefunc, 'task', True)) or pkgwritefunc in (d.getVar('__BBDELTASKS', True) or []):
                # Packaging is disabled for this recipe, we shouldn't do anything
                continue

            if deploydirvarref in sstate_outputdirs:
                # Set intermediate output directory
                d.setVarFlag(pkgwritefunc, 'sstate-outputdirs', sstate_outputdirs.replace(deploydirvarref, deploydirvarref + '-prediff'))

            d.setVar(pkgcomparefunc, d.getVar('do_package_compare', False))
            d.setVarFlags(pkgcomparefunc, d.getVarFlags('do_package_compare', False))
            d.appendVarFlag(pkgcomparefunc, 'depends', ' build-compare-native:do_populate_sysroot')
            bb.build.addtask(pkgcomparefunc, 'do_build', 'do_packagedata ' + pkgwritefunc, d)
}

# This isn't the real task function - it's a template that we use in the
# anonymous python code above
fakeroot python do_package_compare () {
    currenttask = d.getVar('BB_CURRENTTASK', True)
    pkgtype = currenttask.rsplit('_', 1)[1]
    package_compare_impl(pkgtype, d)
}

def package_compare_impl(pkgtype, d):
    import errno
    import fnmatch
    import glob
    import subprocess
    import oe.sstatesig

    pn = d.getVar('PN', True)
    deploydir = d.getVar('DEPLOY_DIR_%s' % pkgtype.upper(), True)
    prepath = deploydir + '-prediff/'

    # Find out PKGR values are
    pkgdatadir = d.getVar('PKGDATA_DIR', True)
    packages = []
    try:
        with open(os.path.join(pkgdatadir, pn), 'r') as f:
            for line in f:
                if line.startswith('PACKAGES:'):
                    packages = line.split(':', 1)[1].split()
                    break
    except IOError as e:
        if e.errno == errno.ENOENT:
            pass

    if not packages:
        bb.debug(2, '%s: no packages, nothing to do' % pn)
        return

    pkgrvalues = {}
    rpkgnames = {}
    rdepends = {}
    pkgvvalues = {}
    for pkg in packages:
        with open(os.path.join(pkgdatadir, 'runtime', pkg), 'r') as f:
            for line in f:
                if line.startswith('PKGR:'):
                    pkgrvalues[pkg] = line.split(':', 1)[1].strip()
                if line.startswith('PKGV:'):
                    pkgvvalues[pkg] = line.split(':', 1)[1].strip()
                elif line.startswith('PKG_%s:' % pkg):
                    rpkgnames[pkg] = line.split(':', 1)[1].strip()
                elif line.startswith('RDEPENDS_%s:' % pkg):
                    rdepends[pkg] = line.split(':', 1)[1].strip()

    # Prepare a list of the runtime package names for packages that were
    # actually produced
    rpkglist = []
    for pkg, rpkg in rpkgnames.iteritems():
        if os.path.exists(os.path.join(pkgdatadir, 'runtime', pkg + '.packaged')):
            rpkglist.append((rpkg, pkg))
    rpkglist.sort(key=lambda x: len(x[0]), reverse=True)

    pvu = d.getVar('PV', False)
    if '$' + '{SRCPV}' in pvu:
        pvprefix = pvu.split('$' + '{SRCPV}', 1)[0]
    else:
        pvprefix = None

    pkgwritetask = 'package_write_%s' % pkgtype
    files = []
    copypkgs = []
    manifest, _ = oe.sstatesig.sstate_get_manifest_filename(pkgwritetask, d)
    with open(manifest, 'r') as f:
        for line in f:
            if line.startswith(prepath):
                srcpath = line.rstrip()
                if os.path.isfile(srcpath):
                    destpath = os.path.join(deploydir, os.path.relpath(srcpath, prepath))

                    # This is crude but should work assuming the output
                    # package file name starts with the package name
                    # and rpkglist is sorted by length (descending)
                    pkgbasename = os.path.basename(destpath)
                    pkgname = None
                    for rpkg, pkg in rpkglist:
                        if pkgbasename.startswith(rpkg):
                            pkgr = pkgrvalues[pkg]
                            destpathspec = destpath.replace(pkgr, '*')
                            if pvprefix:
                                pkgv = pkgvvalues[pkg]
                                if pkgv.startswith(pvprefix):
                                    pkgvsuffix = pkgv[len(pvprefix):]
                                    if '+' in pkgvsuffix:
                                        newpkgv = pvprefix + '*+' + pkgvsuffix.split('+', 1)[1]
                                        destpathspec = destpathspec.replace(pkgv, newpkgv)
                            pkgname = pkg
                            break
                    else:
                        bb.warn('Unable to map %s back to package' % pkgbasename)
                        destpathspec = destpath

                    oldfiles = glob.glob(destpathspec)
                    oldfile = None
                    docopy = True
                    if oldfiles:
                        oldfile = oldfiles[-1]
                        result = subprocess.call(['pkg-diff.sh', oldfile, srcpath])
                        if result == 0:
                            docopy = False

                    files.append((pkgname, pkgbasename, srcpath, oldfile, destpath))
                    bb.debug(2, '%s: package %s %s' % (pn, files[-1], docopy))
                    if docopy:
                        copypkgs.append(pkgname)

    # Ensure that dependencies on specific versions (such as -dev on the
    # main package) are copied in lock-step
    changed = True
    while changed:
        rpkgdict = {x[0]: x[1] for x in rpkglist}
        changed = False
        for pkgname, pkgbasename, srcpath, oldfile, destpath in files:
            rdeps = rdepends.get(pkgname, None)
            if not rdeps:
                continue
            rdepvers = bb.utils.explode_dep_versions2(rdeps)
            for rdep, versions in rdepvers.iteritems():
                dep = rpkgdict.get(rdep, None)
                for version in versions:
                    if version and version.startswith('= '):
                        if dep in copypkgs and not pkgname in copypkgs:
                            bb.debug(2, '%s: copying %s because it has a fixed version dependency on %s and that package is going to be copied' % (pn, pkgname, dep))
                            changed = True
                            copypkgs.append(pkgname)
                        elif pkgname in copypkgs and not dep in copypkgs:
                            bb.debug(2, '%s: copying %s because %s has a fixed version dependency on it and that package is going to be copied' % (pn, dep, pkgname))
                            changed = True
                            copypkgs.append(dep)

    # Read in old manifest so we can delete any packages we aren't going to replace or preserve
    pcmanifest = os.path.join(prepath, d.expand('pkg-compare-manifest-${MULTIMACH_TARGET_SYS}-${PN}'))
    try:
        with open(pcmanifest, 'r') as f:
            knownfiles = [x[3] for x in files if x[3]]
            for line in f:
                fn = line.rstrip()
                if fn:
                    if fn in knownfiles:
                        knownfiles.remove(fn)
                    else:
                        try:
                            os.remove(fn)
                            bb.warn('Removed old package %s' % fn)
                        except OSError as e:
                            if e.errno == errno.ENOENT:
                                pass
    except IOError as e:
        if e.errno == errno.ENOENT:
            pass

    # Create new manifest
    with open(pcmanifest, 'w') as f:
        for pkgname, pkgbasename, srcpath, oldfile, destpath in files:
            if pkgname in copypkgs:
                bb.warn('Copying %s' % pkgbasename)
                destdir = os.path.dirname(destpath)
                bb.utils.mkdirhier(destdir)
                if oldfile:
                    try:
                        os.remove(oldfile)
                    except OSError as e:
                        if e.errno == errno.ENOENT:
                            pass
                if (os.stat(srcpath).st_dev == os.stat(destdir).st_dev):
                    # Use a hard link to save space
                    os.link(srcpath, destpath)
                else:
                    shutil.copyfile(srcpath, destpath)
                f.write('%s\n' % destpath)
            else:
                bb.warn('Not copying %s' % pkgbasename)
                f.write('%s\n' % oldfile)


do_cleanall_append() {
    import errno
    for pkgclass in (d.getVar('PACKAGE_CLASSES', True) or '').split():
        if pkgclass.startswith('package_'):
            pkgtype = pkgclass.split('_', 1)[1]
            deploydir = d.getVar('DEPLOY_DIR_%s' % pkgtype.upper(), True)
            prepath = deploydir + '-prediff'
            pcmanifest = os.path.join(prepath, d.expand('pkg-compare-manifest-${MULTIMACH_TARGET_SYS}-${PN}'))
            try:
                with open(pcmanifest, 'r') as f:
                    for line in f:
                        fn = line.rstrip()
                        if fn:
                            try:
                                os.remove(fn)
                            except OSError as e:
                                if e.errno == errno.ENOENT:
                                    pass
                os.remove(pcmanifest)
            except IOError as e:
                if e.errno == errno.ENOENT:
                    pass
}
