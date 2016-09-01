SUMMARY = "Minimal image for doing Python profiling (for PGO)"

IMAGE_FEATURES += "ssh-server-dropbear"
IMAGE_INSTALL = "packagegroup-core-boot"
IMAGE_INSTALL += "python3-profile-opt python3-profile-opt-tests python3-profile-opt-tools"

LICENSE = "MIT"

inherit core-image

PROFILE_DATA_WORKDIR = "${WORKDIR}/profile-data"

PYTHON3_PROFILE_DIR ?= "${TMPDIR}/work-shared/${MACHINE}/python3/pgo-data"
PYTHON3_PROFILE_TASK_DEFAULT = "-m test.regrtest --pgo -w -x test_asyncore test_gdb test_multiprocessing_fork test_multiprocessing_forkserver test_multiprocessing_main_handling test_multiprocessing_spawn test_subprocess"
# Exclude tests that are failing on qemux86
PYTHON3_PROFILE_TASK_DEFAULT += "test_builtin test_cmath test_concurrent_futures test_difflib test_distutils test_float test_format test_math test_optparse test_shutil test_statistics test_types test_unicode"
# Exclude tests that are taking very long on qemux86
PYTHON3_PROFILE_TASK_DEFAULT += "test_lib2to3 test_buffer test_pickle test_io test_threading test_asyncio test_urllib2_localnet test_itertools test_tuple test_trace test_tarfile test_unicodedata test_decimal test_long test_zipfile test_deque test_descr test_email test_venv test_bytes test_compileall test_ast test_multibytecodec"change python3 profile target to pybenchchange python3 profile target to pybench

# Change default profile target to pybench. Running test.regrtest takes
# ridiculously long, i.e. around 4 hours in qemux86 on i7-3770K desktop
# machine. Pybench "only" takes around 55 minutes.
PYTHON3_PROFILE_TASK_DEFAULT = "${docdir}/python3-profile-opt/Tools/pybench/pybench.py -n 2 --with-gc --with-syscheck"

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


# Profile task for Python 3.x
python do_profile() {
    run_profile(d, 'python3',
                d.getVar('PYTHON3_PROFILE_TASK', True),
                '/home/root/python3-pgo-profiles/',
                os.path.join(d.getVar("PROFILE_DATA_WORKDIR", True), 'python3'))
}

addtask profile after do_build
do_profile[depends] += "qemu-native:do_populate_sysroot qemu-helper-native:do_populate_sysroot"
do_profile[cleandirs] = "${PROFILE_DATA_WORKDIR}/python3"

python do_profile_setscene () {
    sstate_setscene(d)
}

SSTATETASKS += "do_profile"
do_profile[sstate-inputdirs] = "${PROFILE_DATA_WORKDIR}/python3"
do_profile[sstate-outputdirs] = "${PYTHON3_PROFILE_DIR}"
addtask do_profile_setscene
