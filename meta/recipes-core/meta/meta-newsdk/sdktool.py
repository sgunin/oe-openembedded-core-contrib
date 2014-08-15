#!/usr/bin/env python

# SDK tool
#
# Copyright (C) 2014 Intel Corporation
#
# Licensed under the MIT license, see COPYING.MIT for details

import sys
import os
import argparse

# FIXME these should be written out when producing the SDK
bitbake_subdir = 'layers/poky/bitbake'
init_subdir = 'layers/poky'
init_script = 'oe-init-build-env'

basepath = os.path.abspath(__file__)
if '/sysroots/' in basepath:
    basepath = basepath.split('/sysroots/')[0]


def read_workspace():
    workspace = {}
    with open(basepath + '/conf/work-config.inc', 'r') as f:
        for line in f:
            if line.startswith('EXTERNALSRC_'):
                splitval = line.split('=', 2)
                recipe = splitval[0].split('_pn-', 2)[1].rstrip()
                value = splitval[1].strip('" \n\r\t')
                workspace[recipe] = value
    return workspace

def create_recipe(pn):
    import bb
    recipedir = os.path.join(basepath, 'workspace', pn)
    bb.utils.mkdirhier(recipedir)
    # FIXME: Obviously this is very crude, but we don't have a means of creating a proper recipe automatically yet
    with open(os.path.join(recipedir, "%s.bb" % pn), 'w') as f:
        f.write('LICENSE = "CLOSED"\n')
        f.write('inherit autotools\n')

def add(args):
    workspace = read_workspace()
    if args.recipename in workspace:
        print("Error: recipe %s is already in your workspace" % args.recipename)
        return -1

    # FIXME we should probably do these as bbappends to avoid reparsing
    with open(basepath + '/conf/work-config.inc', 'a') as f:
        f.write('EXTERNALSRC_pn-%s = "%s"\n' % (args.recipename, args.srctree))

    create_recipe(args.recipename)

    return 0

def status(args):
    workspace = read_workspace()
    for recipe, value in workspace.iteritems():
        print("%s: %s" % (recipe, value))
    return 0

def build(args):
    import bb
    stdout, stderr = bb.process.run('. %s %s ; bitbake -c install -b %s.bb' % (os.path.join(basepath, init_subdir, init_script), basepath, args.recipename), cwd=basepath)
    print stdout
    print stderr

    return 0

def main():
    global basepath

    parser = argparse.ArgumentParser(description="SDK tool")
    parser.add_argument('--basepath', help='Base directory of SDK')

    subparsers = parser.add_subparsers()
    parser_add = subparsers.add_parser('add', help='Add a new recipe')
    parser_add.add_argument('recipename', help='Name for new recipe to add')
    parser_add.add_argument('srctree', help='Path to external source tree')
    parser_add.set_defaults(func=add)

    parser_status = subparsers.add_parser('status', help='Show status')
    parser_status.set_defaults(func=status)

    parser_build = subparsers.add_parser('build', help='Build recipe')
    parser_build.add_argument('recipename', help='Recipe to build')
    parser_build.set_defaults(func=build)

    args = parser.parse_args()

    if args.basepath:
        basepath = args.basepath

    if not os.path.exists(basepath + '/conf/work-config.inc'):
        print('Error: basepath %s is not valid' % basepath)
        return -1

    sys.path.insert(0, os.path.join(basepath, bitbake_subdir, 'lib'))

    ret = args.func(args)

    return ret


if __name__ == "__main__":
    try:
        ret = main()
    except Exception:
        ret = 1
        import traceback
        traceback.print_exc(5)
    sys.exit(ret)
