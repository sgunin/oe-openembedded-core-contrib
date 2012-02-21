FILES_${PN} += "${datadir}/icons/hicolor"

DEPENDS += "${@base_contains('DISTRO_FEATURES', 'x11', ['hicolor-icon-theme', '']['${BPN}' == 'hicolor-icon-theme'], '', d)}"

# This could run on the host as icon cache files are architecture independent,
# but there is no gtk-update-icon-cache built natively.
gtk_icon_cache_postinst() {
if [ "x$D" != "x" ]; then
        exit 1
fi
if ! which gtk-update-icon-cache >/dev/null; then
	exit 0
fi

# Update the pixbuf loaders in case they haven't been registered yet
GDK_PIXBUF_MODULEDIR=${libdir}/gdk-pixbuf-2.0/2.10.0/loaders gdk-pixbuf-query-loaders --update-cache

for icondir in /usr/share/icons/* ; do
    if [ -d $icondir ] ; then
        gtk-update-icon-cache -fqt  $icondir
    fi
done
}

gtk_icon_cache_postrm() {
if ! which gtk-update-icon-cache >/dev/null; then
	exit 0
fi
for icondir in /usr/share/icons/* ; do
    if [ -d $icondir ] ; then
        gtk-update-icon-cache -qt  $icondir
    fi
done
}

python populate_packages_append () {
	packages = d.getVar('PACKAGES', True).split()
	pkgdest =  d.getVar('PKGDEST', True)
	
	for pkg in packages:
		icon_dir = '%s/%s/%s/icons' % (pkgdest, pkg, d.getVar('datadir', True))
		if not os.path.exists(icon_dir):
			continue

		if oe.utils.contains('DISTRO_FEATURES', 'x11', True, False, d):
			bb.note("adding hicolor-icon-theme dependency to %s" % pkg)	
			rdepends = d.getVar('RDEPENDS_%s' % pkg, True)
			rdepends = rdepends + ' ' + d.getVar('MLPREFIX') + "hicolor-icon-theme"
			d.setVar('RDEPENDS_%s' % pkg, rdepends)
	
		bb.note("adding gtk-icon-cache postinst and postrm scripts to %s" % pkg)
		
		postinst = d.getVar('pkg_postinst_%s' % pkg, True) or d.getVar('pkg_postinst', True)
		if not postinst:
			postinst = '#!/bin/sh\n'
		postinst += d.getVar('gtk_icon_cache_postinst', True)
		d.setVar('pkg_postinst_%s' % pkg, postinst)

		postrm = d.getVar('pkg_postrm_%s' % pkg, True) or d.getVar('pkg_postrm', True)
		if not postrm:
			postrm = '#!/bin/sh\n'
		postrm += d.getVar('gtk_icon_cache_postrm', True)
		d.setVar('pkg_postrm_%s' % pkg, postrm)
}

