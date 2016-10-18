SUMMARY = "Minimal image for doing Python profiling (for PGO)"

IMAGE_FEATURES += "ssh-server-dropbear"
IMAGE_INSTALL = "packagegroup-core-boot python-profile-opt python-profile-opt-tests"

LICENSE = "MIT"

inherit core-image

PYTHON_PROFILE_DIR ?= "${TMPDIR}/work-shared/${MACHINE}/python/pgo-data"
PYTHON_PROFILE_TASK_DEFAULT = "-m test.regrtest --pgo -w -x test_asyncore test_gdb test_multiprocessing test_subprocess"
# Exclude tests that are segfaulting on qemux86 target
PYTHON_PROFILE_TASK_DEFAULT += "test_bytes test_str test_string test_tuple test_unicode test_userstring test_xmlrpc"
# Exclude tests that are failing on qemux86
PYTHON_PROFILE_TASK_DEFAULT += "test_StringIO test_builtin test_calendar test_cmath test_ctypes test_distutils test_exceptions test_getargs test_gzip test_json test_math test_shutil test_socket test_sqlite test_sysconfig test_traceback test_warnings"
# Exclude tests that are taking very long on qemux86
PYTHON_PROFILE_TASK_DEFAULT += "test_io test_lib2to3 test_itertools"
PYTHON_PROFILE_TASK ?= "${PYTHON_PROFILE_TASK_DEFAULT}"

# We need these because we're utilizing the runtime test helpers from oeqa
TEST_TARGET ?= "qemu"
TEST_QEMUBOOT_TIMEOUT ?= "1000"
TEST_LOG_DIR ?= "${WORKDIR}/qemulogs"
FIND_ROOTFS = "1"

python do_profile() {
    from oeqa.targetcontrol import get_target_controller
    from oe.utils import getstatusoutput

    target = get_target_controller(d)
    target.deploy()
    try:
        # Boot target
        bootparams = None
        if d.getVar('VIRTUAL-RUNTIME_init_manager', True) == 'systemd':
            bootparams = 'systemd.log_level=debug systemd.log_target=console'
        target.start(extra_bootparams=bootparams)

        # Run profile task
        profile_cmd = 'LD_LIBRARY_PATH=/opt/lib /opt/bin/python %s' % d.getVar("PYTHON_PROFILE_TASK", True)
        ret, output = target.run(profile_cmd, timeout=7200)
        if ret:
            bb.fatal("Failed to run profile task on target: %s" % output)
        ret, output = target.run('tar czf pgo-data.tgz -C /home/root/python-pgo-profiles/ .')
        if ret:
            bb.fatal("Failed to archive profile data on target: %s" % output)

        # Retrieve and unpack profile data
        profile_dir = d.getVar("PYTHON_PROFILE_DIR", True)
        target.copy_from('/home/root/pgo-data.tgz', profile_dir)

        profile_tarball = os.path.join(profile_dir, 'pgo-data.tgz')
        ret, output = getstatusoutput('tar xf %s -C %s' % (profile_tarball, profile_dir))
        os.unlink(profile_tarball)
        if ret:
            bb.fatal("Failed to unpack python profile data: %s" % output)
    finally:
        target.stop()

    # Exclude files causing problems in cross-profiling
    excludes = [os.path.join(profile_dir, 'Modules', 'posixmodule.gcda')]
    for path in excludes:
        if os.path.exists(path):
            os.unlink(path)
}

addtask profile after do_build
do_profile[depends] += "qemu-native:do_populate_sysroot qemu-helper-native:do_populate_sysroot"
do_profile[cleandirs] = "${PYTHON_PROFILE_DIR}"
