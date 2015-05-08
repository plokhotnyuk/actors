#!/bin/sh

function setjdk() {
  if [ $# -ne 0 ]; then
    removeFromPath '/System/Library/Frameworks/JavaVM.framework/Home/bin'
    if [ -n "${JAVA_HOME+x}" ]; then
      removeFromPath $JAVA_HOME
    fi
    export JAVA_HOME=`/usr/libexec/java_home -v $@`
    export PATH=$JAVA_HOME/bin:$PATH
  fi
}

function removeFromPath() {
  export PATH=$(echo $PATH | sed -E -e "s;:$1;;" -e "s;$1:?;;")
}

rename_files () {
  NAME="${@}"
  mv outX.txt "out${NAME}.txt"
  mv outX_poolSize1.txt "out${NAME}_poolSize1.txt"
  mv outX_poolSize10.txt "out${NAME}_poolSize10.txt"
  mv outX_poolSize100.txt "out${NAME}_poolSize100.txt"
}

run_tests() {
  setjdk "${@}"
  ./mvnAll.sh &&
  ./mvnAll_poolSize1.sh &&
  ./mvnAll_poolSize10.sh &&
  ./mvnAll_poolSize100.sh
}

run_tests "1.8.0_45" &&
rename_files "2" &&
run_tests "1.7.0_80" &&
rename_files "3"