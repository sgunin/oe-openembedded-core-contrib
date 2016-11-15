SUMMARY = "Minimal image for doing Python profiling (for PGO)"

IMAGE_FEATURES += "ssh-server-dropbear"
IMAGE_INSTALL = "packagegroup-core-boot"
IMAGE_INSTALL += "python-profile-opt python-profile-opt-tests python-profile-opt-tools"
IMAGE_INSTALL += "python-profile-opt3 python-profile-opt3-tests"

LICENSE = "MIT"

inherit core-image

PYTHON_PROFILE_DIR ?= "${TMPDIR}/work-shared/${MACHINE}/python/pgo-data"
PROFILE_DATA_WORKDIR = "${WORKDIR}/profile-data"
#PYTHON_PROFILE_TASK_DEFAULT = "-m test.regrtest --pgo -w -x test_asyncore test_gdb test_multiprocessing test_subprocess"
## Exclude tests that are segfaulting on qemux86 target
#PYTHON_PROFILE_TASK_DEFAULT += "test_bytes test_str test_string test_tuple test_unicode test_userstring test_xmlrpc"
## Exclude tests that are failing on qemux86
#PYTHON_PROFILE_TASK_DEFAULT += "test_StringIO test_builtin test_calendar test_cmath test_ctypes test_distutils test_exceptions test_getargs test_gzip test_json test_math test_shutil test_socket test_sqlite test_sysconfig test_traceback test_warnings"
## Exclude tests that are taking very long on qemux86
#PYTHON_PROFILE_TASK_DEFAULT += "test_io test_lib2to3 test_itertools"
PYTHON_PROFILE_TASK_DEFAULT = "/opt/share/doc/python-profile-opt/Tools/pybench/pybench.py -n 2 --with-gc --with-syscheck"
PYTHON_PROFILE_TASK ?= "${PYTHON_PROFILE_TASK_DEFAULT}"

PYTHON3_PROFILE_DIR ?= "${TMPDIR}/work-shared/${MACHINE}/python3/pgo-data"
PYTHON3_PROFILE_TASK_DEFAULT = "-m test.regrtest --pgo -w -x test_asyncore test_gdb test_multiprocessing_fork test_multiprocessing_forkserver test_multiprocessing_main_handling test_multiprocessing_spawn test_subprocess"

PYTHON3_PROFILE_TASK ?= "${PYTHON3_PROFILE_TASK_DEFAULT}"

# We need these because we're utilizing the runtime test helpers from oeqa
TEST_TARGET ?= "qemu"
TEST_QEMUBOOT_TIMEOUT ?= "1000"
TEST_LOG_DIR ?= "${WORKDIR}/qemulogs"
FIND_ROOTFS = "1"

def run_profile(d, profile_bin, profile_task, tgt_in_dir, host_out_dir):
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
        ret, output = target.run(profile_bin + ' ' + profile_task, timeout=7200)
        if ret:
            bb.fatal("Failed to run profile task on target: %s" % output)
        ret, output = target.run('tar czf pgo-data.tgz -C %s .' % tgt_in_dir)
        if ret:
            bb.fatal("Failed to archive profile data on target: %s" % output)

        # Retrieve and unpack profile data
        target.copy_from('/home/root/pgo-data.tgz', host_out_dir)

        profile_tarball = os.path.join(host_out_dir, 'pgo-data.tgz')
        ret, output = getstatusoutput('tar xf %s -C %s' % (profile_tarball, host_out_dir))
        os.unlink(profile_tarball)
        if ret:
            bb.fatal("Failed to unpack python profile data: %s" % output)
    finally:
        target.stop()


# Profile task for Python2
python do_profile() {
    outdir = os.path.join(d.getVar("PROFILE_DATA_WORKDIR", True), 'python')
    run_profile(d, 'LD_LIBRARY_PATH=/opt/lib /opt/bin/python',
                d.getVar('PYTHON_PROFILE_TASK', True),
                '/home/root/python-pgo-profiles/',
                outdir)
    # Exclude files causing problems in cross-profiling
    excludes = [os.path.join(outdir, 'Modules', 'posixmodule.gcda')]
    for path in excludes:
        if os.path.exists(path):
            os.unlink(path)
}

addtask profile after do_build
do_profile[depends] += "qemu-native:do_populate_sysroot qemu-helper-native:do_populate_sysroot"
do_profile[cleandirs] = "${PROFILE_DATA_WORKDIR}/python"

python do_profile_setscene () {
    sstate_setscene(d)
}

SSTATETASKS += "do_profile"
do_profile[sstate-inputdirs] = "${PROFILE_DATA_WORKDIR}/python"
do_profile[sstate-outputdirs] = "${PYTHON_PROFILE_DIR}"
addtask do_profile_setscene


# Profile task for Python3
python do_profile3() {
    run_profile(d, 'LD_LIBRARY_PATH=/opt/lib /opt/bin/python3',
                d.getVar('PYTHON3_PROFILE_TASK', True),
                '/home/root/python3-pgo-profiles/',
                os.path.join(d.getVar("PROFILE_DATA_WORKDIR", True), 'python3'))
}

addtask profile3 after do_build
do_profile3[depends] += "qemu-native:do_populate_sysroot qemu-helper-native:do_populate_sysroot"
do_profile3[cleandirs] = "${PROFILE_DATA_WORKDIR}/python3"

python do_profile3_setscene () {
    sstate_setscene(d)
}

SSTATETASKS += "do_profile3"
do_profile3[sstate-inputdirs] = "${PROFILE_DATA_WORKDIR}/python3"
do_profile3[sstate-outputdirs] = "${PYTHON3_PROFILE_DIR}"
addtask do_profile3_setscene
