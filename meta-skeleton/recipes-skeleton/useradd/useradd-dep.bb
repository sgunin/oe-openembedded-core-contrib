SUMMARY = "Example recipe for using inherit useradd and reusing group from useradd-example"
DESCRIPTION = "This recipe serves as an example for using features from useradd.bbclass"
SECTION = "examples"
PR = "r1"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

S = "${WORKDIR}"

EXCLUDE_FROM_WORLD = "1"

inherit useradd

# https://www.openembedded.org/pipermail/bitbake-devel/2018-March/009256.html
# says that RDEPENDS:${PN} should be enough, but in my testing we need
# built time dependency as well for groups to exist early enough
DEPENDS = "useradd-example"

RDEPENDS:${PN} = "useradd-example"
RDEPENDS:${PN}-user3 = "useradd-example-user3"

# You must set USERADD_PACKAGES when you inherit useradd. This
# lists which output packages will include the user/group
# creation code.
USERADD_PACKAGES = "${PN} ${PN}-user3"

# USERADD_PARAM specifies command line options to pass to the
# useradd command. Multiple users can be created by separating
# the commands with a semicolon. Here we'll create two users,
# user1 and user2:
USERADD_PARAM:${PN} = "-u 2200 -d /home/user1dep -r -s /bin/bash -G group1 user1dep; -u 2201 -d /home/user2dep -r -s /bin/bash -G group2 user2dep"

# user3 will be managed in the useradd-example-user3 pacakge:
# As an example, we use the -P option to set clear text password for user3
USERADD_PARAM:${PN}-user3 = "-u 2202 -d /home/user3dep -r -s /bin/bash -P 'user3' -G group3 user3dep"

# Prevents do_package failures with:
# debugsources.list: No such file or directory:
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
