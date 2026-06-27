#!/usr/bin/env bash

export JAVA_HOME="${HOME}/.local/jdks/temurin-17"
export ANDROID_SDK_ROOT="${HOME}/Android/Sdk"
export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export PATH="${JAVA_HOME}/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

echo "JAVA_HOME=${JAVA_HOME}"
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
