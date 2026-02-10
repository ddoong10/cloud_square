#!/usr/bin/env bash
set -euo pipefail

WAS_APP_DIR="${WAS_APP_DIR:-/opt/lms/backend}"
WAS_STAGING_DIR="${WAS_STAGING_DIR:-/opt/lms/deploy/was}"
WAS_SERVICE_NAME="${WAS_SERVICE_NAME:-lms-backend}"
WAS_ENV_FILE="${WAS_ENV_FILE:-/opt/lms/backend/.env}"

JAR_SRC="${WAS_STAGING_DIR}/backend/app/lms-backend.jar"
JAR_DST="${WAS_APP_DIR}/lms-backend.jar"

if [ ! -f "${JAR_SRC}" ]; then
  echo "ERROR: ${JAR_SRC} not found"
  exit 1
fi

if [ ! -f "${WAS_ENV_FILE}" ]; then
  echo "ERROR: ${WAS_ENV_FILE} not found"
  echo "TODO: provision runtime env file on WAS instances before deploy"
  exit 1
fi

mkdir -p "${WAS_APP_DIR}"
cp -f "${JAR_SRC}" "${JAR_DST}"

if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files | grep -q "^${WAS_SERVICE_NAME}\\.service"; then
  systemctl restart "${WAS_SERVICE_NAME}"
else
  echo "ERROR: systemd service ${WAS_SERVICE_NAME}.service not found"
  echo "TODO: install deploy/was/lms-backend.service.example with real User/Group first"
  exit 1
fi
