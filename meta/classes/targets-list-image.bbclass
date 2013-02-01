python collect_packages () {
    import oe.packagedata
    if isinstance(e, bb.event.TargetsListGenerated):
        #bb.warn("bb.event.TargetsListGenerated e '%s'" % e._model)
        pkgs_to_install = []
        for pn in e._model:
            data = oe.packagedata.read_pkgdata(pn, e.data)
            excluded = 'EXCLUDE_FROM_WORLD' in data and data['EXCLUDE_FROM_WORLD'] == '1'
            excluded_image = 'EXCLUDE_FROM_WORLD_IMAGE' in data and data['EXCLUDE_FROM_WORLD_IMAGE'] == '1'
            if excluded or excluded_image:
                #bb.warn("Skipping whole '%s' because it's explicitly excluded from world(-image)" % pn)
                continue

            if 'PACKAGES' in data:
	        pkgs = data['PACKAGES'].split()
                if 'EXCLUDE_PACKAGES_FROM_WORLD_IMAGE' in data:
                    excluded_packages = data['EXCLUDE_PACKAGES_FROM_WORLD_IMAGE'].split()
                    for pkg in pkgs:
                        if pkg not in excluded_packages:
                            pkgs_to_install.append(pkg)
#                        else:
#                            bb.warn("Skipping '%s' from '%s' because it's explicitly excluded from world-image" % (pkg, pn))
                else:
                    pkgs_to_install.extend(pkgs)
#            else:
#                bb.warn("No packages in '%s'" % pn)

            bb.warn("pkgs_to_install after '%s': '%s'" % (pn, pkgs_to_install))
        e.data.setVar("IMAGE_INSTALL_pn-targets-list-image", ' '.join(pkgs_to_install))
}

addhandler collect_packages
