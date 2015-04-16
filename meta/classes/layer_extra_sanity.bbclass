# Check conf/distro and conf/machine don't appear in the same layer
def check_machine_distro_dir(sanity_data):
    bblayers = sanity_data.getVar('BBLAYERS', True).split()
    for bblayer in bblayers:
        bblayer = bblayer.rstrip('/')
        # skip checking of the meta layer, it's special
        if bblayer.endswith('meta'):
            continue
        if os.path.exists(bblayer + '/' + 'conf/machine') and os.path.exists(bblayer + '/' + 'conf/distro'):
            bb.warn("Layer %s is providing both distro configurations and machine configurations. It's recommended that a layer should provide at most one of them." % bblayer)

# Check that distro variables such as DISTRO_FEATURES are not being set in machine conf files
def check_distro_vars_machine(sanity_data):
    import re

    bblayers = sanity_data.getVar('BBLAYERS', True).split()
    distro_regex = re.compile("^DISTRO_.*=.*")
    distro_var_match = False

    for bblayer in bblayers:
        bblayer = bblayer.rstrip('/')
        if bblayer.endswith('meta'):
            continue
        # Check .inc and .conf files under machine/conf don't set DISTRO_xxx vars
        for dirpath, dirnames, filenames in os.walk('%s/conf/machine' % bblayer):
            for f in filenames:
                fpath = os.path.join(dirpath, f)
                if fpath.endswith(".inc") or fpath.endswith(".conf"):
                    with open(fpath) as fopen:
                        for line in fopen:
                            if distro_regex.match(line):
                                distro_var_match = True
                                break
                if distro_var_match:
                    break
            if distro_var_match:
                break
        if distro_var_match:
            bb.warn("Layer %s is setting distro specific variables in its machine conf files." % bblayer)
        distro_var_match = False

# Check that a disto/bsp layer is being included but MACHINE or DISTRO is not set to any conf
# file that it provides.
#
# The rational here is that if a BSP layer is supposed to have recipes or bbappend files that
# are only specific for the BSPs it provides. So if MACHINE is not set to any of the
# conf/machine/*.conf file in that layer, very likely the user is accidently including a BSP layer.
# The same logic goes for distro layers.
def check_unneedded_bsp_distro_layer(sanity_data):
    machine = sanity_data.getVar('MACHINE', True)
    distro = sanity_data.getVar('DISTRO', True)
    bblayers = sanity_data.getVar('BBLAYERS', True).split()

    for bblayer in bblayers:
        bblayer = bblayer.rstrip('/')
        if bblayer.endswith('meta'):
            continue
        is_bsp = os.path.exists(bblayer + '/' + 'conf/machine')
        is_distro = os.path.exists(bblayer + '/' + 'conf/distro')
        if is_bsp and not is_distro:
            if not os.path.exists(bblayer + '/' + 'conf/machine/' + machine + '.conf'):
                bb.warn("BSP layer %s is included but MACHINE is not set to any conf file it provides." % bblayer)
        elif not is_bsp and is_distro:
           if not os.path.exists(bblayer + '/' + 'conf/distro/' + distro + '.conf'):
                bb.warn("Distro layer %s is included but DISTRO is not set to any conf file it provides." % bblayer)

# Create a copy of the datastore and finalise it to ensure appends and 
# overrides are set - the datastore has yet to be finalised at ConfigParsed
def copy_data(e):
    sanity_data = bb.data.createCopy(e.data)
    sanity_data.finalize()
    return sanity_data

def layer_extra_check_sanity(sanity_data):
    check_machine_distro_dir(sanity_data)
    check_unneedded_bsp_distro_layer(sanity_data)
    check_distro_vars_machine(sanity_data)

addhandler check_layer_extra_sanity_eventhandler
check_layer_extra_sanity_eventhandler[eventmask] = "bb.event.SanityCheck"
python check_layer_extra_sanity_eventhandler() {
    if bb.event.getName(e) == "SanityCheck":
        sanity_data = copy_data(e)
        if e.generateevents:
            sanity_data.setVar("SANITY_USE_EVENTS", "1")
        layer_extra_check_sanity(sanity_data)
        e.data.setVar("BB_INVALIDCONF", False)
        bb.event.fire(bb.event.SanityCheckPassed(), e.data)

    return
}
