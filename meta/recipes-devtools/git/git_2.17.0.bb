require git.inc

EXTRA_OECONF += "ac_cv_snprintf_returns_bogus=no \
                 ac_cv_fread_reads_directories=${ac_cv_fread_reads_directories=yes} \
                 "
EXTRA_OEMAKE += "NO_GETTEXT=1"

SRC_URI[tarball.md5sum] = "4e1c44b87ecdf41c7538b6c4a95dae08"
SRC_URI[tarball.sha256sum] = "7a0cff35dbb14b77dca6924c33ac9fe510b9de35d5267172490af548ec5ee1b8"
SRC_URI[manpages.md5sum] = "e006bb1e890afce4e55cfafffb34d871"
SRC_URI[manpages.sha256sum] = "41b58c68e90e4c95265c75955ddd5b68f6491f4d57b2f17c6d68e60bbb07ba6a"
