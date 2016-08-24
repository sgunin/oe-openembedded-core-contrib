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

usage () {
cat << EOF
Usage: $script [-h] [-d DL_DIR] [-w WORKDIR] BUILD_TARGET THRESHOLD

Optional arguments:
  -h                show this help and exit.
  -d                DL_DIR to use
  -w                work directory to use
EOF
}

while getopts "hd:w:" opt; do
    case $opt in
        h)  usage
            exit 0
            ;;
        d)  downloaddir=`realpath "$OPTARG"`
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

time_cmd () {
    log "timing $*"
    /usr/bin/time -o time.log -f '%e' $@ &>> "$log_file"
    secs=`cut -f1 -d. time.log`
    log "command took $secs seconds (`s_to_hms $secs`)"
    echo $secs
}

run_cmd () {
    log "running $*"
    $@ &>> "$log_file"
}


#
# MAIN SCRIPT
#
build_target=$1
threshold=`hms_to_s $2`

#Initialize build environment
mkdir -p $workdir
. ./oe-init-build-env "$workdir/build-$git_rev-$timestamp" > /dev/null || exit 255
builddir=`pwd`

echo DL_DIR = \"$downloaddir\" >> conf/local.conf
echo CONNECTIVITY_CHECK_URIS = \"\" >> conf/local.conf

# Do actual build
log "TESTING REVISION $git_rev (#$git_rev_cnt)"
log "fetching sources"
run_cmd bitbake $build_target -c fetchall

log "cleaning up build directory"
run_cmd rm -rf bitbake.lock conf/sanity_info cache tmp sstate-cache

log "syncing and dropping caches"
run_cmd sync
echo 3 | sudo -n -k /usr/bin/tee /proc/sys/vm/drop_caches > /dev/null || exit 255
sleep 2

result=`time_cmd bitbake $build_target` || exit 125

log "removing build directory"
cd $workdir
run_cmd rm -rf $builddir

result_hms=`s_to_hms $result`
threshold_hms=`s_to_hms $threshold`
if [ $result -lt $threshold ]; then
    log "OK ($git_rev): $result ($result_hms) < $threshold ($threshold_hms)"
    exit 0
else
    log "FAIL ($git_rev): $result ($result_hms) >= $threshold ($threshold_hms)"
    exit 1
fi
