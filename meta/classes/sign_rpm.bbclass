# Class for generating signed RPM packages.
#
# Configuration variables used by this class:
# RPM_GPG_PASSPHRASE_FILE
#           Path to a file containing the passphrase of the signing key.
# RPM_GPG_NAME
#           Name of the key to sign with. May be key id or key name.
# RPM_GPG_BACKEND
#           Optional variable for specifying the backend to use for signing.
#           Currently the only available option is 'local', i.e. local signing
#           on the build host.
# GPG_BIN
#           Optional variable for specifying the gpg binary/wrapper to use for
#           signing.
# GPG_PATH
#           Optional variable for specifying the gnupg "home" directory:
# OBSSIGN_DELSIGN
#           Optional variable, effective only when 'obssign' backend is enabled.
#           Set to "1" to remove existing signatures from the RPM packages
#           before signing with obs-sign.
#
inherit sanity

RPM_SIGN_PACKAGES='1'
RPM_GPG_BACKEND ?= 'local'


python () {
    # Check configuration
    required = ['RPM_GPG_NAME']
    if d.getVar('RPM_GPG_BACKEND', True) != 'obssign':
        required.append('RPM_GPG_PASSPHRASE_FILE')
    for var in required:
        if not d.getVar(var, True):
            raise_sanity_error("You need to define %s in the config" % var, d)

    # Set the expected location of the public key
    d.setVar('RPM_GPG_PUBKEY', os.path.join(d.getVar('STAGING_ETCDIR_NATIVE', False),
                                            'RPM-GPG-PUBKEY'))
}

python sign_rpm () {
    import glob
    from oe.gpg_sign import get_signer

    signer = get_signer(d,
                        d.getVar('RPM_GPG_BACKEND', True),
                        d.getVar('RPM_GPG_NAME', True),
                        d.getVar('RPM_GPG_PASSPHRASE_FILE', True))
    rpms = glob.glob(d.getVar('RPM_PKGWRITEDIR', True) + '/*')

    signer.sign_rpms(rpms)
}

do_package_index[depends] += "signing-keys:do_export_public_keys"
do_rootfs[depends] += "signing-keys:do_export_public_keys"
