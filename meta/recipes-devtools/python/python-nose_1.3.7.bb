require python-nose.inc
inherit setuptools

RDEPENDS_${PN}_class-target += "\
  python-logging \
  "
