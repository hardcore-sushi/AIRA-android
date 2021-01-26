#!/bin/bash

if [ -z ${ANDROID_NDK_HOME+x} ]; then
  echo "Error: \$ANDROID_NDK_HOME is not defined."
else
  export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/:$PATH
  cargo check --target aarch64-linux-android
fi
