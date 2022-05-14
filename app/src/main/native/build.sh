#!/bin/bash

if [ -z ${ANDROID_NDK_HOME+x} ]; then
  echo "Error: \$ANDROID_NDK_HOME is not defined."
else
  export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/:$PATH
  export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=aarch64-linux-android21-clang
  export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER=armv7a-linux-androideabi21-clang
  export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER=x86_64-linux-android21-clang
  export CARGO_TARGET_I686_LINUX_ANDROID_LINKER=i686-linux-android21-clang
  declare -a androidABIs=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
  declare -a targets=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android" "i686-linux-android")
  for (( i=0; i < ${#targets[@]}; i++ )) do
    cargo build --target ${targets[i]} --release || exit 1
    TARGET_DIR=../jniLibs/${androidABIs[i]}
    mkdir -p $TARGET_DIR && cp target/${targets[i]}/release/libaira.so $TARGET_DIR
  done
fi
