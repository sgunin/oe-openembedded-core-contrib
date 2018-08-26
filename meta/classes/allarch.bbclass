#
# This class enables allarch only when multilib is not used.
#

inherit ${@oe.utils.ifelse(d.getVar('MULTILIB_VARIANTS'), '', 'allarch-enabled')}
