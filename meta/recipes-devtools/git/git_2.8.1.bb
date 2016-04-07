require git.inc

EXTRA_OECONF += "ac_cv_snprintf_returns_bogus=no \
                 ac_cv_fread_reads_directories=${ac_cv_fread_reads_directories=yes} \
                 "
EXTRA_OEMAKE += "NO_GETTEXT=1"

SRC_URI[tarball.md5sum] = "1a12555182c1e9f781bc30a5c5f9515e"
SRC_URI[tarball.sha256sum] = "cfc66324179b9ed62ee02833f29d39935f4ab66874125a3ab9d5bb9055c0cb67"
SRC_URI[manpages.md5sum] = "60552f15a90b9fcdc1b92b222e2d2379"
SRC_URI[manpages.sha256sum] = "df46de0c172049f935cc3736361b263c5ff289b77077c73053e63ae83fcf43f4"
