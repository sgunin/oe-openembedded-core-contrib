#
# This is for perl modules that use cpanm (App::cpanminus)
#
inherit cpan-base perlnative

# Env var which tells perl if it should use host (no) or target (yes) settings
export PERLCONFIGTARGET = "${@is_target(d)}"
export PERL_ARCHLIB = "${STAGING_LIBDIR}${PERL_OWN_DIR}/perl/${@get_perl_version(d)}"
export LD = "${CCLD}"

# Env var which tells cpanm where to download files
export PERL_CPANM_HOME = "${DL_DIR}/.cpanm"
# Env vars which would be set by local::lib
export PERL_MB_OPT = "--install_base ${PERL_ARCHLIB}"
export PERL_MM_OPT = "INSTALL_BASE=${PERL_ARCHLIB}"
export PERL5LIB = "${PERL_ARCHLIB}:${STAGING_LIBDIR}/perl/"
export PERL_LOCAL_LIB_ROOT = "${libdir}/perl"

#
# We also need to have built libapp-cpanminus-perl-native for
# everything except libapp-cpanminus-perl-native itself (which uses
# this class, but uses itself as the provider of
# libapp-cpanminus-perl)
#
def cpanm_dep_prepend(d):
	if d.getVar('CPANM_DEPS', True):
		return ''
	pn = d.getVar('PN', True)
	if pn in ['libapp-cpanminus-perl', 'libapp-cpanminus-perl-native']:
		return ''
	return 'libapp-cpanminus-perl-native '

DEPENDS_prepend = "${@cpanm_dep_prepend(d)}"

cpanm_do_configure () {
	if [ "${@is_target(d)}" = "yes" ]; then
		# build for target
		. ${STAGING_LIBDIR}/perl/config.sh
	fi

	perl Build.PL --installdirs vendor \
				--destdir ${D} \
				--install_path arch="${libdir}/perl" \
				--install_path script=${bindir} \
				--install_path bin=${bindir} \
				--install_path bindoc=${mandir}/man1 \
				--install_path libdoc=${mandir}/man3 \
}

cpanm_do_compile () {
	cpanm perl Build
}

cpanm_do_install () {
	cpanm perl Build install
}

EXPORT_FUNCTIONS do_configure do_compile do_install
