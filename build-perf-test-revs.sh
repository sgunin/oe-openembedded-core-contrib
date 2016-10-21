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
scriptdir=`dirname $0`

usage () {
cat << EOF
Usage: $script [-h] [-b BUILD_TARGET] [-c COUNT] [-d DL_DIR] [-j MAKE_JOBS] [-t BB_THREADS] [-m TEST_METHOD] [-w WORKDIR] [REV1 [REV2]...]

Optional arguments:
  -h                show this help and exit.
  -b                build target to test
  -c                average over COUNT test runs
  -d                DL_DIR to use
  -j                number of make jobs, i.e. PARALLEL_MAKE to use
  -m                test method (buildtime, buildtime2, tmpsize, esdktime,
                        parsetime)
  -t                number of task threads, i.e. BB_NUMBER_THREADS to use
  -w                work directory to use
EOF
}

while getopts "hb:c:d:j:m:t:w:" opt; do
    case $opt in
        h)  usage
            exit 0
            ;;
        b|c|d|j|m|t|w)  cmd_args+=(-$opt "$OPTARG")
            ;;
        *)  usage
            exit 1
            ;;
    esac
done
shift "$((OPTIND - 1))"

revisions=( HEAD )
if [ $# -ge 1 ]; then
    revisions="$@"
fi

# Check validity of given revisions
for rev in $revisions; do
    git rev-parse $rev -- > /dev/null || exit 1
done

# Run tests
for rev in $revisions; do
   git checkout $rev -- &> /dev/null || exit 1
   $scriptdir/build-perf-bisect.sh "${cmd_args[@]}" -n
   if [ $? -eq 255 ]; then
       echo "build-perf-bisect failed!"
       exit 1
   fi
done
