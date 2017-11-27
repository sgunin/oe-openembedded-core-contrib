require python-nose.inc
inherit setuptools3

do_install_append() {
    mv ${D}${bindir}/nosetests ${D}${bindir}/nosetests3
}
