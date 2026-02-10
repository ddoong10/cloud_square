#!/usr/bin/env bash
set -euo pipefail

WEB_ROOT="${WEB_ROOT:-/var/www/lms}"
WEB_STAGING_DIR="${WEB_STAGING_DIR:-/opt/lms/deploy/web}"

if [ "${WEB_ROOT}" = "/" ] || [ -z "${WEB_ROOT}" ]; then
  echo "ERROR: WEB_ROOT is unsafe (${WEB_ROOT})"
  exit 1
fi

if [ ! -d "${WEB_STAGING_DIR}/frontend" ]; then
  echo "ERROR: ${WEB_STAGING_DIR}/frontend not found"
  exit 1
fi

rm -rf "${WEB_ROOT:?}/"*
cp -a "${WEB_STAGING_DIR}/frontend/." "${WEB_ROOT}/"

if command -v nginx >/dev/null 2>&1; then
  nginx -t
fi

if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files | grep -q '^nginx\.service'; then
  systemctl reload nginx
fi
