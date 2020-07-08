# Save the work directory for a recipe when one of its tasks fails.
# Useful in cases where the environment in which builds are run is
# ephemeral or otherwise inaccessible for examination during
# debugging.
#
# To enable, simply add the following to your configuration:
#
# INHERIT += "workdir_save"
#
# Notes:
# * For this to work you also need corresponding logic in your build
#   orchestration tool to pick up any files written out to WORKDIR_SAVE_DIR
#   (with the other assumption being that no files are present there at
#   the start of the build).
# * Work directories can be quite large, so saving them can take some time
#   and of course space.
#
# Copyright (c) 2020 Microsoft Corporation
#
# SPDX-License-Identifier: GPL-2.0-only
#

WORKDIR_SAVE_DIR ?= "${TMPDIR}/workdir_save"
WORKDIR_SAVE_ENABLED ?= "1"

addhandler workdir_save_handler
workdir_save_handler[eventmask] = "bb.build.TaskFailed"

python workdir_save_handler() {
    import datetime
    if d.getVar('WORKDIR_SAVE_ENABLED') != '1':
        return

    base_workdir = d.getVar('BASE_WORKDIR')
    workdir = d.getVar('WORKDIR')
    outdir = d.getVar('WORKDIR_SAVE_DIR')
    bb.utils.mkdirhier(outdir)
    pn = d.getVar('PN')
    tstamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    tarfn = 'workdir_%s_%s.tar.gz' % (pn, tstamp)
    tarfp = os.path.join(outdir, tarfn)
    taskname = d.getVar('BB_CURRENTTASK')
    bb.plain('NOTE: Saving workdir for failed task %s.do_%s to %s' % (pn, taskname, tarfp))
    try:
        bb.process.run(['tar', 'czf', tarfp,
                os.path.relpath(workdir, base_workdir)], cwd=base_workdir)
    except bb.process.ExecutionError as e:
        # It is possible for other tasks to be writing to the workdir
        # while we are tarring it up, in which case tar will return 1,
        # but we don't care in this situation (tar returns 2 for other
        # errors so we we will see those)
        if e.exitcode != 1:
            bb.warn('workdir save error: %s' % str(e))
}
