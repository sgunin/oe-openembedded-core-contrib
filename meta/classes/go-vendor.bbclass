# Copyright 2022 (C) Weidmueller GmbH & Co KG
# Author: Lukas Funke <lukas.funke@weidmueller.com>
#
# Handle Go vendor support for offline builds
#
# When importing Go modules, Go downloads the imported module using
# a network (proxy) connection ahead of the compile stage. This contradicts 
# the yocto build concept of fetching every source ahead of build-time
# and supporting offline builds.
#
# To support offline builds, we use Go 'vendoring': module dependencies are 
# downloaded during the fetch-phase and unpacked into the modules 'vendor'
# folder. Additinally a manifest file is generated for the 'vendor' folder
# 

inherit go-mod

def go_src_uri(repo, path=None, subdir=None, vcs='git', destsuffix_prefix = 'git/src/import/vendor.fetch'):
    module_path = repo if not path else path
    src_uri = "{}://{};name={};destsuffix={}/{}".format(vcs, repo, \
                                    module_path.replace('/', '.'), \
                                    destsuffix_prefix, module_path)

    src_uri += ";subdir={}".format(subdir) if subdir else ""
    src_uri += ";nobranch=1;protocol=https" if vcs == "git" else ""

    return src_uri

def go_generate_vendor_manifest(d):

    vendor_dir = os.path.join(os.path.basename(d.getVar('S')),
                                        'src', d.getVar('GO_IMPORT'), "vendor")
    dst = os.path.join(vendor_dir, "modules.txt")

    go_modules = d.getVarFlags("GO_MODULE_PATH")
    with open(dst, "w") as manifest:
        for go_module in go_modules:
            module_path = d.getVarFlag("GO_MODULE_PATH", go_module)
            module_version = d.getVarFlag("GO_MODULE_VERSION", go_module)
            if module_path and module_version:
                manifest.write("# %s %s\n" % (module_path, module_version))
                manifest.write("## explicit\n")
                exclude = set(['vendor'])
                for subdir, dirs, files in os.walk(os.path.join(vendor_dir, module_path), topdown=True):
                    dirs[:] = [d for d in dirs if d not in exclude]
                    for file in files:
                        if file.endswith(".go"):
                            manifest.write(subdir[len(vendor_dir)+1:] + "\n")
                            break

python go_do_unpack:append() {
    src_uri = (d.getVar('SRC_URI') or "").split()
    if len(src_uri) == 0:
        return

    try:
        fetcher = bb.fetch2.Fetch(src_uri, d)
        src_folder = os.path.join(os.path.basename(d.getVar('S')),
                                        'src', d.getVar('GO_IMPORT'))
        vendor_src = os.path.join(src_folder, "vendor")
        vendor_dst = os.path.join(d.getVar('S'), "src", "import", "vendor.fetch")

        os.symlink(os.path.relpath(vendor_dst, src_folder), vendor_src)
        go_generate_vendor_manifest(d)

    except bb.fetch2.BBFetchException as e:
        raise bb.build.FuncFailed(e)
}
