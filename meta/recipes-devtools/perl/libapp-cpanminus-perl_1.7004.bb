SUMMARY = "App::cpanminus - get, unpack, build and install modules from CPAN"
DESCRIPTION = "cpanminus is a script to get, unpack, build and install\
modules from CPAN and does nothing else.\
It's dependency free (can bootstrap itself), requires zero configuration,\
and stands alone. When running, it requires only 10MB of RAM.\
"
HOMEPAGE = "https://github.com/miyagawa/cpanminus"
SECTION = "devel"
LICENSE = "Artistic-1.0 | GPL-1.0+"

LIC_FILES_CHKSUM = "file://LICENSE;md5=a5d414485de391eb1920bd7bc066c6a0"

SRC_URI = "http://search.cpan.org/CPAN/authors/id/M/MI/MIYAGAWA/App-cpanminus-${PV}.tar.gz"
SRC_URI[md5sum] = "83b0b8353be83b0e9f01e9227aea4e4f"
SRC_URI[sha256sum] = "57ee310081bd30fa103caad9d80e68e03f838f7b39ec70dfa5d771c6d7ef9284"

S = "${WORKDIR}/App-cpanminus-${PV}"

inherit cpan

BBCLASSEXTEND = "native"

