#!/bin/bash
#
# Copyright (c) 2016, Intel Corporation.
#
# This program is free software; you can redistribute it and/or modify it
# under the terms and conditions of the GNU General Public License,
# version 2, as published by the Free Software Foundation.
#
# This program is distributed in the hope it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
# more details.
#
#
# This script is a simple wrapper around the actual build performance tester
# script. This script initializes the build environment, runs
# oe-build-perf-test and archives the results.

#
# PARSE COMMAND LINE ARGUMENTS
#
script=`basename $0`
workdir=`realpath build-perf-bisect`
test_method="buildtime"

usage () {
cat << EOF
Usage: $script [-h] [-d DL_DIR] [-m TEST_METHOD] [-w WORKDIR] BUILD_TARGET THRESHOLD

Optional arguments:
  -h                show this help and exit.
  -d                DL_DIR to use
  -m                test method, available options are:
                        buildtime, tmpsize, esdktime (default: $test_method)
  -w                work directory to use
EOF
}

while getopts "hd:m:w:" opt; do
    case $opt in
        h)  usage
            exit 0
            ;;
        d)  downloaddir=`realpath "$OPTARG"`
            ;;
        m)  test_method="$OPTARG"
            ;;
        w)  workdir=`realpath "$OPTARG"`
            ;;
        *)  usage
            exit 255
            ;;
    esac
done
shift "$((OPTIND - 1))"

if [ $# -ne 2 ]; then
    echo "Invalid number of positional arguments ($# instead of 2)"
    usage
    exit 255
fi

# Initialize rest of variables
[ -z "$downloaddir" ] && downloaddir="$workdir/downloads"
timestamp=`date "+%Y%m%d_%H%M%S"`
git_rev=`git rev-parse --short HEAD`
git_rev_cnt=`git rev-list --count HEAD`
log_file="$workdir/bisect-${git_rev_cnt}_g${git_rev}-${timestamp}.log"


#
# HELPER FUNCTIONS
#
log () {
    echo "[`date '+%Y-%m-%d %H:%M:%S'`] $@" | tee -a "$log_file" >&2
}

hms_to_s () {
    _f1=`echo $1: | cut -d ':' -f 1`
    _f2=`echo $1: | cut -d ':' -f 2`
    _f3=`echo $1: | cut -d ':' -f 3`

    if echo "$_f1$_f2$_f3" | grep -q -e '[^0-9.]'; then
        log "invalid time stamp format: '$1'"
        exit 255
    fi

    #>&2 echo "'$_f1' '$_f2' '$_f3'"
    if [ -z "$_f2" ]; then
        _s=`echo $_f1 | cut -d '.' -f 1`
    elif [ -z "$_f3" ]; then
        _ss=`echo $_f2 | cut -d '.' -f 1`
        _s=$((_f1*60 + $_ss))
    else
        _ss=`echo $_f3 | cut -d '.' -f 1`
        _s=$(($_f1*3600 + $_f2*60 + $_ss))
    fi

    echo $_s
}

s_to_hms () {
    if echo "$1" | grep -q -e '[^0-9]'; then
        log "not an integer: '$1'"
        exit 255
    fi

    printf "%d:%02d:%02d" $(($1 / 3600)) $((($1 % 3600) / 60)) $(($1 % 60))
}

kib_to_gib () {
    echo `echo -e "scale=2\n$1 / 1024^2" | bc -l`G
}

time_cmd () {
    log "timing $*"
    /usr/bin/time -o time.log -f '%e' $@ &>> "$log_file"
    if [ $? -ne 0 ]; then
        log "ERROR: command failed, see $log_file for details"
        return 255
    fi
    secs=`cut -f1 -d. time.log`
    log "command took $secs seconds (`s_to_hms $secs`)"
    echo $secs
}

run_cmd () {
    log "running $*"
    $@ &>> "$log_file"
    if [ $? -ne 0 ]; then
        log "ERROR: command failed, see $log_file for details"
        return 255
    fi
}

do_sync () {
    run_cmd sync || exit 255
    log "dropping kernel caches"
    echo 3 | sudo -n -k /usr/bin/tee /proc/sys/vm/drop_caches > /dev/null || exit 255
    sleep 2
}


#
# TEST METHODS
#
buildtime () {
    log "cleaning up build directory"
    run_cmd rm -rf bitbake.lock conf/sanity_info cache tmp sstate-cache

    log "syncing and dropping caches"
    do_sync

    result=`time_cmd bitbake $1` || exit 125
    result_h=`s_to_hms $result`

    log "removing build directory"
    cd $workdir
    run_cmd rm -rf $builddir
}

tmpsize () {
    log "cleaning up build directory"
    run_cmd rm -rf bitbake.lock conf/sanity_info cache tmp sstate-cache

    log "syncing and dropping caches"
    do_sync

    _time=`time_cmd bitbake $1` || exit 125

    result=`du -s tmp* | cut -f1` || exit 255
    result_h=`kib_to_gib $result`

    log "removing build directory"
    cd $workdir
    run_cmd rm -rf $builddir
}

esdktime () {
    run_cmd rm -rf esdk-deploy
    _time=`time_cmd bitbake $1 -c populate_sdk_ext` || exit 125

    esdk_installer=(tmp/deploy/sdk/*-toolchain-ext-*.sh)
    if [ ${#esdk_installer[@]} -gt 1 ]; then
        log "Found ${#esdk_installer[@]} eSDK installers"
    fi

    do_sync

    result=`time_cmd "${esdk_installer[-1]}" -y -d "esdk-deploy"` || exit 125
    result_h=`s_to_hms $result`

    log "removing deploy directories"
    run_cmd rm -rf esdk-deploy tmp*
}


#
# MAIN SCRIPT
#
build_target=$1

builddir="$workdir/build-$git_rev-$timestamp"
case "$test_method" in
    buildtime)
        threshold=`hms_to_s $2`
        threshold_h=`s_to_hms $threshold`
        ;;
    tmpsize)
        threshold=$2
        threshold_h=`kib_to_gib $2`
        ;;
    esdktime)
        threshold=`hms_to_s $2`
        threshold_h=`s_to_hms $threshold`
        builddir="$workdir/build"
        ;;
    *)
        echo "Invalid test method $test_method"
        exit 255
esac


#Initialize build environment
mkdir -p $workdir
. ./oe-init-build-env "$builddir" > /dev/null || exit 255

echo DL_DIR = \"$downloaddir\" >> conf/local.conf
echo CONNECTIVITY_CHECK_URIS = \"\" >> conf/local.conf

# Do actual build
log "TESTING REVISION $git_rev (#$git_rev_cnt)"
log "fetching sources"
run_cmd bitbake $build_target -c fetchall || exit 125

$test_method $build_target

if [ $result -lt $threshold ]; then
    log "OK ($git_rev): $result ($result_h) < $threshold ($threshold_h)"
    exit 0
else
    log "FAIL ($git_rev): $result ($result_h) >= $threshold ($threshold_h)"
    exit 1
fi
