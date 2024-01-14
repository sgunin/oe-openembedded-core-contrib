SRCBRANCH ?= "master"
PV = "2.39+git"
SRCREV_glibc ?= "87d921e270acd614077764228881ffcf5e21d9f7"
SRCREV_localedef ?= "fab74f31b3811df543e24b6de47efdf45b538abc"

GLIBC_GIT_URI ?= "git://sourceware.org/git/glibc.git;protocol=https"

UPSTREAM_CHECK_GITTAGREGEX = "(?P<pver>\d+\.\d+(\.(?!90)\d+)*)"

CVE_STATUS[CVE-2023-4527] = "fixed-version: Fixed in stable branch updates"
CVE_STATUS[CVE-2023-4911] = "fixed-version: Fixed in stable branch updates"
CVE_STATUS[CVE-2023-4806] = "fixed-version: Fixed in stable branch updates"
CVE_STATUS[CVE-2023-5156] = "fixed-version: Fixed in stable branch updates"
CVE_STATUS[CVE-2023-4527] = "fixed-version: Fixed in stable branch updates"
CVE_STATUS[CVE-2023-0687] = "fixed-version: Fixed in stable branch updates"
