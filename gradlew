#!/usr/bin/env bash

set -euo pipefail

APP_HOME="$(cd -- "$(dirname "$0")" && pwd)"
GRADLE_VERSION="9.3.1"
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
DIST_ROOT="${APP_HOME}/gradle-dist"
DIST_DIR="${DIST_ROOT}/gradle-${GRADLE_VERSION}"
ZIP_PATH="${DIST_ROOT}/${DIST_NAME}"
LOCAL_JAVA_HOME="${HOME}/.local/jdks/temurin-17"

if [ -z "${JAVA_HOME:-}" ] && [ -x "${LOCAL_JAVA_HOME}/bin/java" ]; then
  export JAVA_HOME="${LOCAL_JAVA_HOME}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

mkdir -p "${DIST_ROOT}"

if [ ! -x "${DIST_DIR}/bin/gradle" ]; then
  rm -rf "${DIST_DIR}" "${ZIP_PATH}"
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  curl -fsSL "${DIST_URL}" -o "${ZIP_PATH}"
  unzip -q "${ZIP_PATH}" -d "${DIST_ROOT}"
fi

exec "${DIST_DIR}/bin/gradle" "$@"
