#!/bin/sh

# for each tune file generates list of available tunes
# then for each combination of machine and tune lists PACKAGE_ARCHS, CC, TUNE_CCARGS*, TUNE_PKGARCH

# To have the same sorting rules
export LANG=en_US.UTF-8

TUNE_TEST_DIR=`dirname $0`

INCLUDES_TO_TEST=`find oe-core/meta/conf/machine/include/ -name tune-\*.inc | sort | sed 's%oe-core/meta/%%g'`
INCLUDES=`echo "${INCLUDES_TO_TEST}" | wc -l`
INCLUDE=0

# It's recommended to add BB_SERVER_TIMEOUT = "60"
# to local.conf

for I in ${INCLUDES_TO_TEST}; do
  M=`basename ${I} | sed 's/\.inc//g; s/^tune-/fake-/g'`
  INCLUDE=`expr ${INCLUDE} + 1`
  echo "Testing fake MACHINE ${M} for include ${I} (${INCLUDE}/${INCLUDES})"
  echo "require ${I}" > oe-core/meta/conf/machine/${M}.conf
  MACHINE=${M} bitbake -e openssl > ${TUNE_TEST_DIR}/log.${M} 2>&1
  grep "\(^export CC=\)\|\(^TUNE_CCARGS\)\|\(^TUNE_FEATURES=\)\|\(^PACKAGE_ARCHS=\)\|\(^TUNE_PKGARCH=\)\|\(^AVAILTUNES=\)" \
    ${TUNE_TEST_DIR}/log.${M} | sort |\
      sed "s#--sysroot=[^ \"]*/recipe-sysroot#--sysroot=SYSROOT#g" \
        > ${TUNE_TEST_DIR}/env.${M}
  if [ `cat ${TUNE_TEST_DIR}/env.${M} | wc -l` -lt 6 ] ; then
    echo "ERROR: something wrong in ${TUNE_TEST_DIR}/log.${M}"
    rm -f ${TUNE_TEST_DIR}/env.${M}
    continue
  else
    rm -f ${TUNE_TEST_DIR}/log.${M}
  fi
  AVAILTUNES=`grep '^AVAILTUNES=' ${TUNE_TEST_DIR}/env.${M} | sed 's/^[^"]*"\([^"]*\)"/\1/g' | tr ' ' '\n'`;
  TUNES=`echo "${AVAILTUNES}" | wc -l`
  TUNE=0
  for T in ${AVAILTUNES}; do
    TUNE=`expr ${TUNE} + 1`
    echo "Testing DEFAULTTUNE ${T} (${TUNE}/${TUNES}) for fake MACHINE ${M} (${INCLUDE}/${INCLUDES})"
    echo "DEFAULTTUNE = \"${T}\"" >> oe-core/meta/conf/machine/${M}.conf;
    MACHINE=${M} bitbake -e openssl > ${TUNE_TEST_DIR}/log.${M}.${T} 2>&1
    grep "\(^export CC=\)\|\(^TUNE_CCARGS\)\|\(^TUNE_FEATURES=\)\|\(^PACKAGE_ARCHS=\)\|\(^TUNE_PKGARCH=\)" \
      ${TUNE_TEST_DIR}/log.${M}.${T} | sort |\
        sed "s#--sysroot=[^ \"]*/recipe-sysroot#--sysroot=SYSROOT#g" \
          > ${TUNE_TEST_DIR}/env.${M}.${T}
    if [ `cat ${TUNE_TEST_DIR}/env.${M}.${T} | wc -l` -lt 5 ] ; then
      echo "ERROR: something wrong in ${TUNE_TEST_DIR}/log.${M}.${T}"
      rm -f ${TUNE_TEST_DIR}/env.${M}.${T}
      continue
    else
      rm -f ${TUNE_TEST_DIR}/log.${M}.${T}
    fi
  done
done
