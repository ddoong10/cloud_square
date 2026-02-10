#!/usr/bin/env bash
set -euo pipefail

WEB_HEALTH_URL="${WEB_HEALTH_URL:-http://127.0.0.1/}"

curl -fsS "${WEB_HEALTH_URL}" >/dev/null

