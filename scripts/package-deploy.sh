#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"
BUILD_DIR="${DIST_DIR}/.build"

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/was/package"
mkdir -p "${DIST_DIR}"

echo "[1/2] Build backend jar"
pushd "${ROOT_DIR}/backend" >/dev/null
./gradlew --no-daemon clean bootJar
popd >/dev/null

JAR_PATH="$(find "${ROOT_DIR}/backend/build/libs" -maxdepth 1 -type f -name "*.jar" ! -name "*plain*" | head -n 1)"
if [ -z "${JAR_PATH}" ]; then
  echo "ERROR: backend jar not found in backend/build/libs"
  exit 1
fi

echo "[2/2] Assemble WAS deploy bundle"
mkdir -p "${BUILD_DIR}/was/package/backend/app" "${BUILD_DIR}/was/scripts"
cp "${JAR_PATH}" "${BUILD_DIR}/was/package/backend/app/lms-backend.jar"
cp "${ROOT_DIR}/deploy/was/appspec.yml" "${BUILD_DIR}/was/appspec.yml"
cp "${ROOT_DIR}"/deploy/was/scripts/*.sh "${BUILD_DIR}/was/scripts/"
chmod +x "${BUILD_DIR}"/was/scripts/*.sh

tar -C "${BUILD_DIR}/was" -czf "${DIST_DIR}/lms-was-deploy.tar.gz" .

echo "Done"
echo " - ${DIST_DIR}/lms-was-deploy.tar.gz"
echo ""
echo "Frontend is deployed to CDN via: scripts/upload-frontend-cdn.js"

