#!/usr/bin/env bash
set -euo pipefail

WEB_ROOT="${WEB_ROOT:-/var/www/lms}"
WEB_STAGING_DIR="${WEB_STAGING_DIR:-/opt/lms/deploy/web}"

mkdir -p "${WEB_ROOT}"
mkdir -p "${WEB_STAGING_DIR}"

