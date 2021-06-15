#!/bin/bash

if [ -z ${ANDROID_NDK_HOME+x} ]; then
  echo "Error: \$ANDROID_NDK_HOME is not defined."
else
  export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/:$PATH
  declare -a androidABIs=("arm64-v8a" "armeabi-v7a" "x86")
  declare -a targets=("aarch64-linux-android" "armv7-linux-androideabi" "i686-linux-android")
  for (( i=0; i < ${#targets[@]}; i++ )) do
    cargo build --target ${targets[i]} --release
    cp target/${targets[i]}/release/libaira.so ../jniLibs/${androidABIs[i]}/
  done
fi
