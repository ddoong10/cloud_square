#!/usr/bin/env bash
set -euo pipefail

WAS_HEALTH_URL="${WAS_HEALTH_URL:-http://127.0.0.1:8080/health}"
curl -fsS "${WAS_HEALTH_URL}" >/dev/null

