python do_devshell () {
    import oe.terminal
    oe.terminal.run(d.getVar('SHELL', True), 'OpenEmbedded Developer Shell', d)
}

addtask devshell after do_patch

do_devshell[dirs] = "${S}"
do_devshell[nostamp] = "1"
