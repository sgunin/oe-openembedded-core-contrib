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
build_target="core-image-sato"
workdir=`realpath build-perf-bisect`
test_method="buildtime"
test_count=1
parallel_make='${@oe.utils.cpu_count()}'
bb_number_threads='${@oe.utils.cpu_count()}'

usage () {
cat << EOF
Usage: $script [-h] [-b BUILD_TARGET] [-c COUNT] [-d DL_DIR] [-j MAKE_JOBS] [-t BB_THREADS] [-m TEST_METHOD] [-w WORKDIR] [-n | THRESHOLD]

Optional arguments:
  -h                show this help and exit.
  -b                use BUILD_TARGET for tests involving bitbake build
                        (default: $build_target)
  -c                average over COUNT test runs (default: $test_count)
  -d                DL_DIR to use
  -i                invert logic: values above the threshold are OK, below it
                        FAIL
  -j                number of make jobs, i.e. PARALLEL_MAKE to use in bitbake
                        conf (default: $parallel_make)
  -m                test method, available options are:
                        buildtime, buildtime2, tmpsize, esdktime, esdksize,
                        parsetime (default: $test_method)
  -n                no threshold, do not do any comparison, all successful
                        builds return 0
  -t                number of task threads, i.e. BB_NUMBER_THREADS to use
                        in bitbake conf (default: $bb_number_threads)
  -w                work directory to use
EOF
}

while getopts "hb:c:d:ij:m:nt:w:" opt; do
    case $opt in
        h)  usage
            exit 0
            ;;
        b)  build_target=$OPTARG
            ;;
        c)  test_count=$OPTARG
            ;;
        d)  downloaddir=`realpath "$OPTARG"`
            ;;
        i)  invert_cmp="1"
            ;;
        j)  parallel_make="$OPTARG"
            ;;
        m)  test_method="$OPTARG"
            ;;
        n)  no_threshold="1"
            ;;
        t)  bb_number_threads="$OPTARG"
            ;;
        w)  workdir=`realpath "$OPTARG"`
            ;;
        *)  usage
            exit 255
            ;;
    esac
done
shift "$((OPTIND - 1))"

if [ -z "$no_threshold" -a $# -ne 1 ]; then
    echo "Invalid number of positional arguments. You must use -n or give a threshold"
    usage
    exit 255
fi

# Initialize rest of variables
[ -z "$downloaddir" ] && downloaddir="$workdir/downloads"
timestamp=`date "+%Y%m%d_%H%M%S"`
git_rev=`git rev-parse --short HEAD`
git_rev_cnt=`git rev-list --count HEAD`
log_file="$workdir/bisect-${git_rev_cnt}_g${git_rev}-${timestamp}.log"
buildstats_dir="$workdir/buildstats-${git_rev_cnt}_g${git_rev}-${timestamp}"


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
        _s=$_f1
    elif [ -z "$_f3" ]; then
        _s=`echo "$_f1*60 + $_f2" | bc -l`
    else
        _s=`echo "$_f1*3600 + $_f2*60 + $_f3" | bc -l`
    fi

    echo $_s
}

s_to_hms () {
    if echo "$1" | grep -q -e '[^0-9.]'; then
        log "not a number: '$1'"
        exit 255
    fi

    _h=`echo -e "scale=0\n$1 / 3600" | bc`
    _m=`echo -e "scale=0\n($1 % 3600) / 60" | bc`
    _s=`echo "$1 % 60" | bc`
    if [ $_h -eq 0 ]; then
        printf "%d:%05.2f" $_m $_s
    else
        printf "%d:%02d:%05.2f" $_h $_m $_s
    fi
}

kib_to_gib () {
    echo `echo -e "scale=2\n$1 / 1024^2" | bc -l`G
}

raw_to_h () {
    case $quantity in
        TIME)
            s_to_hms $1
            ;;
        SIZE)
            kib_to_gib $1
            ;;
        *)
            echo "Invalid quantity '$quantity'!"
            exit 255
            ;;
    esac
}

h_to_raw () {
    case $quantity in
        TIME)
            hms_to_s $1
            ;;
        SIZE)
            echo "$1"
            ;;
        *)
            echo "Invalid quantity '$quantity'!"
            exit 255
            ;;
    esac
}


time_cmd () {
    log "timing $*"
    /usr/bin/time -o time.log -f '%e' $@ &>> "$log_file"
    if [ $? -ne 0 ]; then
        log "ERROR: command failed, see $log_file for details"
        return 255
    fi
    secs=`cat time.log`
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

check_sudo () {
    # Check that we're able to run the needed commands as superuser
    output=`echo 0 | sudo -n -k /usr/bin/tee /proc/sys/vm/drop_caches 2>&1`
    if echo $output | grep -q "a password is required"; then
        log "ERROR: insufficient sudo permissions. Fix this e.g. by putting <user> ALL = NOPASSWD: /usr/bin/tee /proc/sys/vm/drop_caches into sudoers file"
        exit 255
    else
        log "sudo permissions OK"
    fi
}

do_sync () {
    run_cmd sync || exit 255
    log "dropping kernel caches"
    echo 3 | sudo -n -k /usr/bin/tee /proc/sys/vm/drop_caches > /dev/null || exit 255
    sleep 2
}

save_buildstats () {
    log "Saving buildstats"
    mkdir -p "$buildstats_dir"
    mv tmp*/buildstats/* "$buildstats_dir"
}

cleanup () {
    $cleanup_func "$@"
}

cleanup_default () {
    cd $workdir
    run_cmd rm -rf $builddir
}


#
# TEST METHODS
#
buildtime () {
    log "cleaning up build directory"
    run_cmd rm -rf bitbake.lock conf/sanity_info cache tmp sstate-cache

    log "syncing and dropping caches"
    do_sync

    results+=(`time_cmd bitbake $1`) || exit 125

    save_buildstats
}

buildtime2 () {
    # Pre-build to get all the deps in place
    _time=`time_cmd bitbake $1` || exit 125
    run_cmd bitbake -c cleansstate $1
    rm -rf tmp*/buildstats/*

    do_sync

    results+=(`time_cmd bitbake $1`) || exit 125

    save_buildstats
}

cleanup_buildtime2 () {
    run_cmd rm -rf tmp*
}

rootfstime () {
    # Pre-build to populate sstate cache
    _time=`time_cmd bitbake $1` || exit 125
    run_cmd rm -rf tmp*

    do_sync

    results+=(`time_cmd bitbake -c rootfs $1`) || exit 125

    save_buildstats
}

cleanup_rootfstime () {
    run_cmd rm -rf tmp*
}

tmpsize () {
    log "cleaning up build directory"
    run_cmd rm -rf bitbake.lock conf/sanity_info cache tmp sstate-cache

    log "syncing and dropping caches"
    do_sync

    _time=`time_cmd bitbake $1` || exit 125

    results+=(`du -s tmp* | cut -f1`) || exit 255
}

esdk_common () {
    run_cmd rm -rf esdk-deploy
    _time=`time_cmd bitbake $1 -c populate_sdk_ext` || exit 125

    esdk_installer=(tmp/deploy/sdk/*-toolchain-ext-*.sh)
    if [ ${#esdk_installer[@]} -gt 1 ]; then
        log "Found ${#esdk_installer[@]} eSDK installers"
    fi

    do_sync

    _time=`time_cmd "${esdk_installer[-1]}" -y -d "esdk-deploy"` || exit 125
    _size=`du -s --apparent-size -B1024 esdk-deploy | cut -f1` || exit 255
}

esdktime () {
    esdk_common "$@"
    results+=($_time)
}

esdksize () {
    esdk_common "$@"
    results+=($_size)
}

cleanup_esdk () {
    run_cmd rm -rf esdk-deploy tmp*
}

parsetime () {
    run_cmd rm -rf bitbake.lock conf/sanity_info cache tmp sstate-cache

    do_sync
    results+=(`time_cmd bitbake -p`) || exit 125
}


#
# MAIN SCRIPT
#
cleanup_func=cleanup_default
quantity='TIME'


builddir="$workdir/build-$git_rev-$timestamp"
case "$test_method" in
    buildtime)
        ;;
    buildtime2)
        builddir="$workdir/build"
        cleanup_func=cleanup_buildtime2
        ;;
    rootfstime)
        builddir="$workdir/build"
        cleanup_func=cleanup_rootfstime
        ;;
    tmpsize)
        quantity="SIZE"
        ;;
    esdktime)
        builddir="$workdir/build"
        cleanup_func=cleanup_esdk
        ;;
    esdksize)
        builddir="$workdir/build"
        cleanup_func=cleanup_esdk
        quantity="SIZE"
        ;;
    parsetime)
        build_target=""
        ;;
    *)
        echo "Invalid test method $test_method"
        exit 255
esac

if [ -z "$no_threshold" ]; then
    threshold=`h_to_raw $1`
    threshold_h=`raw_to_h $threshold`
fi

trap cleanup EXIT


#Initialize build environment
mkdir -p $workdir
. ./oe-init-build-env "$builddir" > /dev/null || exit 255

echo DL_DIR = \"$downloaddir\" >> conf/local.conf
echo CONNECTIVITY_CHECK_URIS = \"\" >> conf/local.conf
echo PARALLEL_MAKE = \"-j $parallel_make\" >> conf/local.conf
echo BB_NUMBER_THREADS = \"$bb_number_threads\" >> conf/local.conf

# Do actual build
log "TESTING REVISION $git_rev (#$git_rev_cnt), AVERAGING OVER $test_count TEST RUNS"
check_sudo

log "fetching sources"
if [ -n "$build_target" ]; then
    run_cmd bitbake $build_target -c fetchall || exit 125
fi

results=()
i=0
while [ $i -lt $test_count ]; do
    log "TEST RUN #$i on $git_rev (#$git_rev_cnt)"
    $test_method $build_target
    i=$((i + 1))
done

# Calculate average over results
bc_expression="scale=2\n( `printf "%s+" ${results[@]}` 0) / ${#results[@]}"
result=`echo -e "$bc_expression" | bc`
result_h=`raw_to_h $result`

log "Raw results: ${results[@]}"

if [ -n "$threshold" ]; then
    if [ `echo "$result < $threshold" | bc` -eq 1 ]; then
        if [ -z "$invert_cmp" ]; then
            log "OK ($git_rev): $result ($result_h) < $threshold ($threshold_h)"
            exit 0
        else
            log "FAIL (inv) ($git_rev): $result ($result_h) < $threshold ($threshold_h)"
            exit 1
        fi
    else
        if [ -z "$invert_cmp" ]; then
            log "FAIL ($git_rev): $result ($result_h) >= $threshold ($threshold_h)"
            exit 1
        else
            log "OK (inv) ($git_rev): $result ($result_h) >= $threshold ($threshold_h)"
            exit 0
        fi
    fi
else
    log "OK ($git_rev): $result ($result_h)"
    exit 0
fi
