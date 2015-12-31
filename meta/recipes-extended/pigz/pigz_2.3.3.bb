require pigz.inc
LIC_FILES_CHKSUM = "file://pigz.c;beginline=7;endline=21;md5=a21d4075cb00ab4ca17fce5e7534ca95"

SRC_URI += "file://link-order.patch"

SRC_URI[md5sum] = "01d7a16cce77929cc1a78aa1bdfb68cb"
SRC_URI[sha256sum] = "4e8b67b432ce7907575a549f3e1cac4709781ba0f6b48afea9f59369846b509c"

NATIVE_PACKAGE_PATH_SUFFIX = "/${PN}"

BBCLASSEXTEND = "native nativesdk"

# fails with thumb
# | arm-linux-gnueabi-gcc  -march=armv7ve -mthumb  -mthumb-interwork -mfpu=neon-vfpv4  -mfloat-abi=hard -mcpu=cortex-a7 -mtune=cortex-a7 -funwind-tables -rdynamic  --sysroot=SYSROOT  -O2 -pipe -g -feliminate-unused-debug-types    -c -o pigz.o pigz.c
# | {standard input}: Assembler messages:
# | {standard input}:4693: Error: offset out of range
# | {standard input}:4694: Error: offset out of range
# | make: *** [pigz.o] Error 1
ARM_INSTRUCTION_SET = "arm"
