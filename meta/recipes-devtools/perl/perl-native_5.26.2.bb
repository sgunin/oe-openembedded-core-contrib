require perl.inc

# We need gnugrep (for -I)
DEPENDS = "db-native grep-native gdbm-native zlib-native"

EXTRA_OEMAKE = "-e MAKEFLAGS="

#file://debian/hppa_opmini_optimize_workaround.diff
#file://debian/hurd-softupdates.diff
#file://debian/no_packlist_perllocal.diff
#file://debian/prune_libs.diff
#file://debian/sh4_op_optimize_workaround.diff
#file://debian/writable_site_dirs.diff
#file://fixes/rename-filexp.U-phase2.diff
#file://fixes/test-printf-null.diff
SRC_URI += "\
        file://debian/configure-regen.diff \
        file://debian/cpan_definstalldirs.diff \
        file://debian/cpan-missing-site-dirs.diff \
        file://debian/db_file_ver.diff \
        file://debian/deprecate-with-apt.diff \
        file://debian/doc_info.diff \
        file://debian/enc2xs_inc.diff \
        file://debian/errno_ver.diff \
        file://debian/extutils_set_libperl_path.diff \
        file://debian/fakeroot.diff \
        file://debian/find_html2text.diff \
        file://debian/hppa_op_optimize_workaround.diff \
        file://debian/installman-utf8.diff \
        file://debian/instmodsh_doc.diff \
        file://debian/kfreebsd-softupdates.diff \
        file://debian/ld_run_path.diff \
        file://debian/libnet_config_path.diff \
        file://debian/libperl_embed_doc.diff \
        file://debian/makemaker-manext.diff \
        file://debian/makemaker-pasthru.diff \
        file://debian/mod_paths.diff \
        file://debian/patchlevel.diff \
        file://debian/perl5db-x-terminal-emulator.patch \
        file://debian/perldoc-pager.diff \
        file://debian/perlivp.diff \
        file://debian/squelch-locale-warnings.diff \
        file://fixes/autodie-scope.diff \
        file://fixes/cpan_web_link.diff \
        file://fixes/CVE-2018-12015-Archive-Tar-directory-traversal.diff \
        file://fixes/CVE-2018-6797-testcase.diff \
        file://fixes/document_makemaker_ccflags.diff \
        file://fixes/encode-alias-regexp.diff \
        file://fixes/extutils_file_path_compat.diff \
        file://fixes/extutils_makemaker_reproducible.diff \
        file://fixes/file_path_chmod_race.diff \
        file://fixes/file_path_hurd_errno.diff \
        file://fixes/getopt-long-2.diff \
        file://fixes/getopt-long-3.diff \
        file://fixes/getopt-long-4.diff \
        file://fixes/json-pp-example.diff \
        file://fixes/math_complex_doc_angle_units.diff \
        file://fixes/math_complex_doc_great_circle.diff \
        file://fixes/math_complex_doc_see_also.diff \
        file://fixes/memoize-pod.diff \
        file://fixes/memoize_storable_nstore.diff \
        file://fixes/packaging_test_skips.diff \
        file://fixes/rename-filexp.U-phase1.diff \
        file://fixes/respect_umask.diff \
        file://fixes/test-builder-reset.diff \
        file://fixes/time_piece_doc.diff \
        "

SRC_URI[md5sum] = "1fa1b53eeff76aa37b17bfc9b2771671"
SRC_URI[sha256sum] = "0f8c0fb1b0db4681adb75c3ba0dd77a0472b1b359b9e80efd79fc27b4352132c"

inherit native

NATIVE_PACKAGE_PATH_SUFFIX = "/${PN}"

export LD="${CCLD}"

do_configure () {
	./Configure \
		-Dcc="${CC}" \
		-Dcflags="${CFLAGS}" \
		-Dldflags="${LDFLAGS}" \
		-Dlddlflags="${LDFLAGS} -shared" \
		-Dcf_by="Open Embedded" \
		-Dprefix=${prefix} \
		-Dvendorprefix=${prefix} \
		-Dsiteprefix=${prefix} \
		\
		-Dbin=${STAGING_BINDIR}/${PN} \
		-Dprivlib=${STAGING_LIBDIR}/perl/${PV} \
		-Darchlib=${STAGING_LIBDIR}/perl/${PV} \
		-Dvendorlib=${STAGING_LIBDIR}/perl/vendor_perl/${PV} \
		-Dvendorarch=${STAGING_LIBDIR}/perl/vendor_perl/${PV} \
		-Dsitelib=${STAGING_LIBDIR}/perl/site_perl/${PV} \
		-Dsitearch=${STAGING_LIBDIR}/perl/site_perl/${PV} \
		\
		-Duseshrplib \
		-Dusethreads \
		-Duseithreads \
		-Duselargefiles \
		-Dnoextensions=ODBM_File \
		-Ud_dosuid \
		-Ui_db \
		-Ui_ndbm \
		-Ui_gdbm \
		-Ui_gdbm_ndbm \
		-Ui_gdbmndbm \
		-Di_shadow \
		-Di_syslog \
		-Duseperlio \
		-Dman3ext=3pm \
		-Dsed=/bin/sed \
		-Uafs \
		-Ud_csh \
		-Uusesfio \
		-Uusenm -des
}

do_install () {
	oe_runmake 'DESTDIR=${D}' install

	# We need a hostperl link for building perl
	ln -sf perl${PV} ${D}${bindir}/hostperl

        ln -sf perl ${D}${libdir}/perl5

	install -d ${D}${libdir}/perl/${PV}/CORE \
	           ${D}${datadir}/perl/${PV}/ExtUtils

	# Save native config 
	install config.sh ${D}${libdir}/perl
	install lib/Config.pm ${D}${libdir}/perl/${PV}/
	install lib/ExtUtils/typemap ${D}${libdir}/perl/${PV}/ExtUtils/

	# perl shared library headers
	# reference perl 5.20.0-1 in debian:
	# https://packages.debian.org/experimental/i386/perl/filelist
	for i in av.h bitcount.h charclass_invlists.h config.h cop.h cv.h dosish.h \
		embed.h embedvar.h EXTERN.h fakesdio.h feature.h form.h git_version.h \
		gv.h handy.h hv_func.h hv.h inline.h INTERN.h intrpvar.h iperlsys.h \
		keywords.h l1_char_class_tab.h malloc_ctl.h metaconfig.h mg_data.h \
		mg.h mg_raw.h mg_vtable.h mydtrace.h nostdio.h opcode.h op.h \
		opnames.h op_reg_common.h overload.h pad.h parser.h patchlevel.h \
		perlapi.h perl.h perlio.h perliol.h perlsdio.h perlvars.h perly.h \
		pp.h pp_proto.h proto.h reentr.h regcharclass.h regcomp.h regexp.h \
		regnodes.h scope.h sv.h thread.h time64_config.h time64.h uconfig.h \
		unicode_constants.h unixish.h utf8.h utfebcdic.h util.h uudmap.h \
		vutil.h warnings.h XSUB.h
	do
		install $i ${D}${libdir}/perl/${PV}/CORE
	done

	# Those wrappers mean that perl installed from sstate (which may change
	# path location) works and that in the nativesdk case, the SDK can be
	# installed to a different location from the one it was built for.
	create_wrapper ${D}${bindir}/perl PERL5LIB='$PERL5LIB:${STAGING_LIBDIR}/perl/site_perl/${PV}:${STAGING_LIBDIR}/perl/vendor_perl/${PV}:${STAGING_LIBDIR}/perl/${PV}'
	create_wrapper ${D}${bindir}/perl${PV} PERL5LIB='$PERL5LIB:${STAGING_LIBDIR}/perl/site_perl/${PV}:${STAGING_LIBDIR}/perl/vendor_perl/${PV}:${STAGING_LIBDIR}/perl/${PV}'

	# Use /usr/bin/env nativeperl for the perl script.
	for f in `grep -Il '#! *${bindir}/perl' ${D}/${bindir}/*`; do
		sed -i -e 's|${bindir}/perl|/usr/bin/env nativeperl|' $f
	done

	# The packlist is large with hardcoded paths meaning it needs relocating
	# so just remove it.
	rm ${D}${libdir}/perl/${PV}/.packlist
}

SYSROOT_PREPROCESS_FUNCS += "perl_sysroot_create_wrapper"

perl_sysroot_create_wrapper () {
	mkdir -p ${SYSROOT_DESTDIR}${bindir}
	# Create a wrapper that /usr/bin/env perl will use to get perl-native.
	# This MUST live in the normal bindir.
	cat > ${SYSROOT_DESTDIR}${bindir}/../nativeperl << EOF
#!/bin/sh
realpath=\`readlink -fn \$0\`
exec \`dirname \$realpath\`/perl-native/perl "\$@"
EOF
	chmod 0755 ${SYSROOT_DESTDIR}${bindir}/../nativeperl
	cat ${SYSROOT_DESTDIR}${bindir}/../nativeperl
}

# Fix the path in sstate
SSTATE_SCAN_FILES += "*.pm *.pod *.h *.pl *.sh"
PACKAGES_DYNAMIC_class-native += "^perl-module-.*native$"

