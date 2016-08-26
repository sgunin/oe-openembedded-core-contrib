SUMMARY = "Documentation generator for glib-based software"
DESCRIPTION = "Gtk-doc is a set of scripts that extract specially formatted comments \
               from glib-based software and produce a set of html documentation files from them"
HOMEPAGE = "http://www.gtk.org/gtk-doc/"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=94d55d512a9ba36caa9b7df079bae19f"

inherit gnomebase pythonnative perlnative
DEPENDS_append = "libxslt xmlto source-highlight-native"
EXTRA_OECONF_append = " --with-highlight=source-highlight"
EXTRA_OECONF_append_class-native = " --with-xml-catalog=${sysconfdir}/xml/catalog.xml"

SRC_URI[archive.md5sum] = "0dc6570953112a464a409fb99258ccbc"
SRC_URI[archive.sha256sum] = "1ea46ed400e6501f975acaafea31479cea8f32f911dca4dff036f59e6464fd42"

BBCLASSEXTEND = "native"

# do not check for XML catalogs when building for target because
# they are not installed into target sysroot, and are not used
# for anything during build
do_configure_prepend_class-target() {
        sed -i -e 's,^JH_CHECK_XML_CATALOG.*,,' ${S}/configure.ac
}

FILES_${PN} += "${datadir}/sgml"
FILES_${PN}-dev += "${libdir}/cmake"
FILES_${PN}-doc = ""

SYSROOT_PREPROCESS_FUNCS_append_class-native = " gtkdoc_makefiles_sysroot_preprocess"
gtkdoc_makefiles_sysroot_preprocess() {
        # Patch the gtk-doc makefiles so that the qemu wrapper is used to run transient binaries
        # instead of libtool wrapper or running them directly
        sed -i \
           -e "s|GTKDOC_RUN =.*|GTKDOC_RUN = \$(top_builddir)/gtkdoc-qemuwrapper|" \
           ${SYSROOT_DESTDIR}${datadir}/gtk-doc/data/gtk-doc*make
}

