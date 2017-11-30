SUMMARY = "WebKit web rendering engine for the GTK+ platform"
HOMEPAGE = "http://www.webkitgtk.org/"
BUGTRACKER = "http://bugs.webkit.org/"

LICENSE = "BSD & LGPLv2+"
LIC_FILES_CHKSUM = "file://Source/JavaScriptCore/COPYING.LIB;md5=d0c6d6397a5d84286dda758da57bd691 \
                    file://Source/WebCore/LICENSE-APPLE;md5=4646f90082c40bcf298c285f8bab0b12 \
		    file://Source/WebCore/LICENSE-LGPL-2;md5=36357ffde2b64ae177b2494445b79d21 \
		    file://Source/WebCore/LICENSE-LGPL-2.1;md5=a778a33ef338abbaf8b8a7c36b6eec80 \
		   "

SRC_URI = "http://www.webkitgtk.org/releases/${BPN}-${PV}.tar.xz \
           file://0001-FindGObjectIntrospection.cmake-prefix-variables-obta.patch \
           file://0001-When-building-introspection-files-add-CMAKE_C_FLAGS-.patch \
           file://0001-OptionsGTK.cmake-drop-the-hardcoded-introspection-gt.patch \
           file://musl-fixes.patch \
           file://ppc-musl-fix.patch \
           file://0001-Fix-racy-parallel-build-of-WebKit2-4.0.gir.patch \
           file://0001-Tweak-gtkdoc-settings-so-that-gtkdoc-generation-work.patch \
           file://x32_support.patch \
           file://cross-compile.patch \
           file://gcc7.patch \
           file://detect-atomics-during-configure.patch \
           file://0001-WebKitMacros-Append-to-I-and-not-to-isystem.patch \
           "

SRC_URI[md5sum] = "0e2d142a586e4ff79cf0324f4fdbf20c"
SRC_URI[sha256sum] = "fc23650df953123c59b9c0edf3855e7bd55bd107820997fc72375811e1ea4b21"

inherit cmake pkgconfig gobject-introspection perlnative distro_features_check upstream-version-is-even gtk-doc

# depends on libxt
REQUIRED_DISTRO_FEATURES = "x11"

DEPENDS = "zlib libsoup-2.4 curl libxml2 cairo libxslt libxt libidn libgcrypt \
           gtk+3 gstreamer1.0 gstreamer1.0-plugins-base flex-native gperf-native sqlite3 \
	   pango icu bison-native gawk intltool-native libwebp \
	   atk udev harfbuzz jpeg libpng pulseaudio librsvg libtheora libvorbis libxcomposite libxtst \
	   ruby-native libnotify gstreamer1.0-plugins-bad \
	   gettext-native glib-2.0 glib-2.0-native \
          "

PACKAGECONFIG ??= "${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'x11', 'wayland' ,d)} \
                   ${@bb.utils.contains('DISTRO_FEATURES', 'opengl', 'webgl opengl', '' ,d)} \
                   enchant \
                   libsecret \
                  "

PACKAGECONFIG[wayland] = "-DENABLE_WAYLAND_TARGET=ON,-DENABLE_WAYLAND_TARGET=OFF,wayland"
PACKAGECONFIG[x11] = "-DENABLE_X11_TARGET=ON,-DENABLE_X11_TARGET=OFF,virtual/libx11"
PACKAGECONFIG[geoclue] = "-DENABLE_GEOLOCATION=ON,-DENABLE_GEOLOCATION=OFF,geoclue"
PACKAGECONFIG[enchant] = "-DENABLE_SPELLCHECK=ON,-DENABLE_SPELLCHECK=OFF,enchant"
PACKAGECONFIG[gtk2] = "-DENABLE_PLUGIN_PROCESS_GTK2=ON,-DENABLE_PLUGIN_PROCESS_GTK2=OFF,gtk+"
PACKAGECONFIG[gles2] = "-DENABLE_GLES2=ON,-DENABLE_GLES2=OFF,virtual/libgles2"
PACKAGECONFIG[webgl] = "-DENABLE_WEBGL=ON,-DENABLE_WEBGL=OFF,virtual/libgl"
PACKAGECONFIG[opengl] = "-DENABLE_OPENGL=ON,-DENABLE_OPENGL=OFF,virtual/libgl"
PACKAGECONFIG[libsecret] = "-DUSE_LIBSECRET=ON,-DUSE_LIBSECRET=OFF,libsecret"
PACKAGECONFIG[libhyphen] = "-DUSE_LIBHYPHEN=ON,-DUSE_LIBHYPHEN=OFF,libhyphen"

EXTRA_OECMAKE = " \
		-DPORT=GTK \
		-DCMAKE_BUILD_TYPE=Release \
		${@bb.utils.contains('GI_DATA_ENABLED', 'True', '-DENABLE_INTROSPECTION=ON', '-DENABLE_INTROSPECTION=OFF', d)} \
		${@bb.utils.contains('GTKDOC_ENABLED', 'True', '-DENABLE_GTKDOC=ON', '-DENABLE_GTKDOC=OFF', d)} \
		-DENABLE_MINIBROWSER=ON \
                -DPYTHON_EXECUTABLE=`which python` \
		"

# Javascript JIT is not supported on powerpc
EXTRA_OECMAKE_append_powerpc = " -DENABLE_JIT=OFF "
EXTRA_OECMAKE_append_powerpc64 = " -DENABLE_JIT=OFF "

# ARM JIT code does not build on ARMv4/5/6 anymore
EXTRA_OECMAKE_append_armv5 = " -DENABLE_JIT=OFF "
EXTRA_OECMAKE_append_armv6 = " -DENABLE_JIT=OFF "
EXTRA_OECMAKE_append_armv4 = " -DENABLE_JIT=OFF "

# binutils 2.25.1 has a bug on aarch64:
# https://sourceware.org/bugzilla/show_bug.cgi?id=18430
EXTRA_OECMAKE_append_aarch64 = " -DUSE_LD_GOLD=OFF "
EXTRA_OECMAKE_append_mipsarch = " -DUSE_LD_GOLD=OFF "
EXTRA_OECMAKE_append_powerpc = " -DUSE_LD_GOLD=OFF "
EXTRA_OECMAKE_append_toolchain-clang = " -DUSE_LD_GOLD=OFF "

EXTRA_OECMAKE_append_aarch64 = " -DWTF_CPU_ARM64_CORTEXA53=ON"

# JIT not supported on MIPS either
EXTRA_OECMAKE_append_mipsarch = " -DENABLE_JIT=OFF "

# JIT not supported on X32
# An attempt was made to upstream JIT support for x32 in
# https://bugs.webkit.org/show_bug.cgi?id=100450, but this was closed as
# unresolved due to limited X32 adoption.
EXTRA_OECMAKE_append_linux-gnux32 = " -DENABLE_JIT=OFF"

SECURITY_CFLAGS_remove_aarch64 = "-fpie"
SECURITY_CFLAGS_append_aarch64 = " -fPIE"

FILES_${PN} += "${libdir}/webkit2gtk-4.0/injected-bundle/libwebkit2gtkinjectedbundle.so"

RRECOMMENDS_${PN} += "ca-certificates shared-mime-info"

# http://errors.yoctoproject.org/Errors/Details/20370/
ARM_INSTRUCTION_SET_armv4 = "arm"
ARM_INSTRUCTION_SET_armv5 = "arm"
ARM_INSTRUCTION_SET_armv6 = "arm"

# https://bugzilla.yoctoproject.org/show_bug.cgi?id=9474
# https://bugs.webkit.org/show_bug.cgi?id=159880
# JSC JIT can build on ARMv7 with -marm, but doesn't work on runtime.
# Upstream only tests regularly the JSC JIT on ARMv7 with Thumb2 (-mthumb).
ARM_INSTRUCTION_SET_armv7a = "thumb"
ARM_INSTRUCTION_SET_armv7r = "thumb"
ARM_INSTRUCTION_SET_armv7ve = "thumb"

# qemu: uncaught target signal 11 (Segmentation fault) - core dumped
# Segmentation fault
GI_DATA_ENABLED_armv7a = "False"
GI_DATA_ENABLED_armv7ve = "False"

do_configure[postfuncs] += 'shorter_flags_make'

python shorter_flags_make() {
    recipe_sysroot = d.getVar('RECIPE_SYSROOT')
    for root, dirs, files in os.walk(d.getVar('B')):
        for flags_make in files:
            if flags_make == 'flags.make':
                # To fix build error when len(TMPDIR) == 410:
                # - Replace -I${RECIPE_SYSROOT} with -I=
                # - Replace "-I${S}/path1/in/S -I ${S}/path2/in/S" with
                #   "-iprefix ${S} -iwithprefixbefore /path1/in/S -iwithprefixbefore /path2/in/S"
                flags_make = os.path.join(root, flags_make)
                new_lines = []
                with open(flags_make, 'r') as f:
                    for line in f.readlines():
                        if line.startswith('CXX_INCLUDES = ') or line.startswith('C_INCLUDES = '):
                            line = line.replace('-I%s' % recipe_sysroot, '-I=')
                            line = line.replace('CXX_INCLUDES =', 'CXX_INCLUDES = -iprefix %s/ ' % d.getVar('S'))
                            line = line.replace('C_INCLUDES =', 'C_INCLUDES = -iprefix %s/ ' % d.getVar('S'))
                            line = line.replace('-I%s' % d.getVar('S'), '-iwithprefixbefore ')
                        new_lines.append(line)

                with open(flags_make, 'w') as f:
                        for line in new_lines:
                            f.write(line)
}
