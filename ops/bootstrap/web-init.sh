#!/usr/bin/env bash
set -euo pipefail

exec > >(tee -a /var/log/lms-web-init.log) 2>&1

# Fill these in Launch Template UserData before use.
NCP_DEPLOY_ACCESS_KEY="${NCP_DEPLOY_ACCESS_KEY:-TODO_NCP_DEPLOY_ACCESS_KEY}"
NCP_DEPLOY_SECRET_KEY="${NCP_DEPLOY_SECRET_KEY:-TODO_NCP_DEPLOY_SECRET_KEY}"
NCP_S3_ENDPOINT="${NCP_S3_ENDPOINT:-https://kr.object.ncloudstorage.com}"
NCP_S3_REGION="${NCP_S3_REGION:-kr-standard}"
NCP_DEPLOY_BUCKET="${NCP_DEPLOY_BUCKET:-TODO_DEPLOY_BUCKET}"
NCP_DEPLOY_PREFIX="${NCP_DEPLOY_PREFIX:-lms-deploy}"
WEB_ROOT="${WEB_ROOT:-/var/www/lms}"
WAS_UPSTREAM_HOST="${WAS_UPSTREAM_HOST:-TODO_INTERNAL_LB_HOST}"
WAS_UPSTREAM_PORT="${WAS_UPSTREAM_PORT:-80}"
WEB_RATE_LIMIT_RPS="${WEB_RATE_LIMIT_RPS:-20r/s}"
WEB_RATE_LIMIT_BURST="${WEB_RATE_LIMIT_BURST:-60}"
WEB_CONN_LIMIT="${WEB_CONN_LIMIT:-50}"
SYNC_INTERVAL_MINUTES="${SYNC_INTERVAL_MINUTES:-2}"

require_value() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" || "${value}" == TODO_* ]]; then
    echo "ERROR: ${key} is not configured"
    exit 1
  fi
}

require_value "NCP_DEPLOY_ACCESS_KEY" "${NCP_DEPLOY_ACCESS_KEY}"
require_value "NCP_DEPLOY_SECRET_KEY" "${NCP_DEPLOY_SECRET_KEY}"
require_value "NCP_DEPLOY_BUCKET" "${NCP_DEPLOY_BUCKET}"
require_value "WAS_UPSTREAM_HOST" "${WAS_UPSTREAM_HOST}"

install_packages() {
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y nginx curl tar awscli rsync
    return
  fi
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y nginx curl tar awscli rsync
    return
  fi
  if command -v yum >/dev/null 2>&1; then
    yum install -y nginx curl tar awscli rsync
    return
  fi
  echo "ERROR: unsupported package manager"
  exit 1
}

install_packages

mkdir -p /etc/lms
cat > /etc/lms/bootstrap.env <<EOF
AWS_ACCESS_KEY_ID=${NCP_DEPLOY_ACCESS_KEY}
AWS_SECRET_ACCESS_KEY=${NCP_DEPLOY_SECRET_KEY}
AWS_DEFAULT_REGION=${NCP_S3_REGION}
NCP_S3_ENDPOINT=${NCP_S3_ENDPOINT}
NCP_DEPLOY_BUCKET=${NCP_DEPLOY_BUCKET}
NCP_DEPLOY_PREFIX=${NCP_DEPLOY_PREFIX}
WEB_ROOT=${WEB_ROOT}
EOF
chmod 600 /etc/lms/bootstrap.env

mkdir -p "${WEB_ROOT}" /opt/lms/web /var/lib/lms-web

cat > /usr/local/bin/lms-web-sync.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

exec 9>/var/lock/lms-web-sync.lock
flock -n 9 || exit 0

set -a
source /etc/lms/bootstrap.env
set +a

VERSION_KEY="${NCP_DEPLOY_PREFIX}/web/latest/version.txt"
ARTIFACT_KEY="${NCP_DEPLOY_PREFIX}/web/latest/lms-web-deploy.tar.gz"
STATE_DIR="/var/lib/lms-web"
LOCAL_VERSION_FILE="${STATE_DIR}/version.txt"
TMP_DIR="$(mktemp -d)"
FORCE="${1:-}"

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

mkdir -p "${STATE_DIR}" "${WEB_ROOT}"

remote_version="$(aws --endpoint-url "${NCP_S3_ENDPOINT}" --region "${AWS_DEFAULT_REGION}" s3 cp "s3://${NCP_DEPLOY_BUCKET}/${VERSION_KEY}" - 2>/dev/null | tr -d '\r\n' || true)"
if [[ -z "${remote_version}" ]]; then
  echo "ERROR: could not read remote version"
  exit 1
fi

local_version=""
if [[ -f "${LOCAL_VERSION_FILE}" ]]; then
  local_version="$(cat "${LOCAL_VERSION_FILE}" | tr -d '\r\n')"
fi

if [[ "${FORCE}" != "--force" && "${local_version}" == "${remote_version}" ]]; then
  echo "lms-web-sync: already latest (${remote_version})"
  exit 0
fi

artifact_path="${TMP_DIR}/lms-web-deploy.tar.gz"
aws --endpoint-url "${NCP_S3_ENDPOINT}" --region "${AWS_DEFAULT_REGION}" \
  s3 cp "s3://${NCP_DEPLOY_BUCKET}/${ARTIFACT_KEY}" "${artifact_path}"

tar -xzf "${artifact_path}" -C "${TMP_DIR}"

if [[ ! -d "${TMP_DIR}/package/frontend" ]]; then
  echo "ERROR: invalid artifact layout"
  exit 1
fi

rsync -a --delete "${TMP_DIR}/package/frontend/" "${WEB_ROOT}/"
echo "${remote_version}" > "${LOCAL_VERSION_FILE}"

nginx -t
systemctl reload nginx || systemctl restart nginx
echo "lms-web-sync: deployed ${remote_version}"
EOF
chmod +x /usr/local/bin/lms-web-sync.sh

cat > /etc/nginx/conf.d/lms.conf <<EOF
limit_req_zone \$binary_remote_addr zone=lms_req:20m rate=${WEB_RATE_LIMIT_RPS};
limit_conn_zone \$binary_remote_addr zone=lms_conn:20m;

server {
    listen 80 default_server;
    server_name _;
    root ${WEB_ROOT};
    index index.html;
    client_max_body_size 1024m;

    location = /health {
        access_log off;
        default_type text/plain;
        return 200 "OK\n";
    }

    location /api/ {
        limit_req zone=lms_req burst=${WEB_RATE_LIMIT_BURST} nodelay;
        limit_conn lms_conn ${WEB_CONN_LIMIT};

        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
        proxy_pass http://${WAS_UPSTREAM_HOST}:${WAS_UPSTREAM_PORT};
    }

    location / {
        limit_req zone=lms_req burst=${WEB_RATE_LIMIT_BURST} nodelay;
        limit_conn lms_conn ${WEB_CONN_LIMIT};
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

rm -f /etc/nginx/sites-enabled/default || true
rm -f /etc/nginx/conf.d/default.conf || true

cat > /etc/systemd/system/lms-web-sync.service <<'EOF'
[Unit]
Description=LMS WEB artifact sync
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/lms-web-sync.sh
EOF

cat > /etc/systemd/system/lms-web-sync.timer <<EOF
[Unit]
Description=LMS WEB artifact sync timer

[Timer]
OnBootSec=45s
OnUnitActiveSec=${SYNC_INTERVAL_MINUTES}min
Unit=lms-web-sync.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable nginx
systemctl start nginx

/usr/local/bin/lms-web-sync.sh --force
systemctl enable --now lms-web-sync.timer

echo "lms-web-init: completed"
