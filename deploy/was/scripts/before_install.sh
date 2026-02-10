#!/usr/bin/env bash
set -euo pipefail

WAS_APP_DIR="${WAS_APP_DIR:-/opt/lms/backend}"
WAS_STAGING_DIR="${WAS_STAGING_DIR:-/opt/lms/deploy/was}"

mkdir -p "${WAS_APP_DIR}"
mkdir -p "${WAS_STAGING_DIR}"

