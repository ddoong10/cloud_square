#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"
BUILD_DIR="${DIST_DIR}/.build"

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/web/package" "${BUILD_DIR}/was/package"
mkdir -p "${DIST_DIR}"

echo "[1/4] Build backend jar"
pushd "${ROOT_DIR}/backend" >/dev/null
./gradlew --no-daemon clean bootJar
popd >/dev/null

JAR_PATH="$(find "${ROOT_DIR}/backend/build/libs" -maxdepth 1 -type f -name "*.jar" ! -name "*plain*" | head -n 1)"
if [ -z "${JAR_PATH}" ]; then
  echo "ERROR: backend jar not found in backend/build/libs"
  exit 1
fi

echo "[2/4] Assemble WAS deploy bundle"
mkdir -p "${BUILD_DIR}/was/package/backend/app" "${BUILD_DIR}/was/scripts"
cp "${JAR_PATH}" "${BUILD_DIR}/was/package/backend/app/lms-backend.jar"
cp "${ROOT_DIR}/deploy/was/appspec.yml" "${BUILD_DIR}/was/appspec.yml"
cp "${ROOT_DIR}"/deploy/was/scripts/*.sh "${BUILD_DIR}/was/scripts/"
chmod +x "${BUILD_DIR}"/was/scripts/*.sh

echo "[3/4] Assemble WEB deploy bundle"
mkdir -p "${BUILD_DIR}/web/package/frontend" "${BUILD_DIR}/web/scripts"
cp -R "${ROOT_DIR}/frontend/." "${BUILD_DIR}/web/package/frontend/"
cp "${ROOT_DIR}/deploy/web/appspec.yml" "${BUILD_DIR}/web/appspec.yml"
cp "${ROOT_DIR}"/deploy/web/scripts/*.sh "${BUILD_DIR}/web/scripts/"
chmod +x "${BUILD_DIR}"/web/scripts/*.sh

echo "[4/4] Create deploy artifacts"
tar -C "${BUILD_DIR}/was" -czf "${DIST_DIR}/lms-was-deploy.tar.gz" .
tar -C "${BUILD_DIR}/web" -czf "${DIST_DIR}/lms-web-deploy.tar.gz" .

echo "Done"
echo " - ${DIST_DIR}/lms-was-deploy.tar.gz"
echo " - ${DIST_DIR}/lms-web-deploy.tar.gz"

