#!/usr/bin/env bash
set -euo pipefail

exec > >(tee -a /var/log/lms-was-init.log) 2>&1

# Fill these in Launch Template UserData before use.
NCP_DEPLOY_ACCESS_KEY="${NCP_DEPLOY_ACCESS_KEY:-TODO_NCP_DEPLOY_ACCESS_KEY}"
NCP_DEPLOY_SECRET_KEY="${NCP_DEPLOY_SECRET_KEY:-TODO_NCP_DEPLOY_SECRET_KEY}"
NCP_S3_ENDPOINT="${NCP_S3_ENDPOINT:-https://kr.object.ncloudstorage.com}"
NCP_S3_REGION="${NCP_S3_REGION:-kr-standard}"
NCP_DEPLOY_BUCKET="${NCP_DEPLOY_BUCKET:-lms-static}"
NCP_DEPLOY_PREFIX="${NCP_DEPLOY_PREFIX:-uploads/lms-deploy}"
SYNC_INTERVAL_MINUTES="${SYNC_INTERVAL_MINUTES:-2}"

# Runtime env for backend service.
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
DB_HOST="${DB_HOST:-TODO_DB_HOST}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-lms-db}"
DB_USER="${DB_USER:-user}"
DB_PASSWORD="${DB_PASSWORD:-TODO_DB_PASSWORD}"
JWT_SECRET="${JWT_SECRET:-TODO_JWT_SECRET}"
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="${JWT_ACCESS_TOKEN_EXPIRATION_SECONDS:-3600}"
CORS_ALLOWED_ORIGIN="${CORS_ALLOWED_ORIGIN:-http://127.0.0.1:*,http://localhost:*,https://lms.uoscholar-server.store,https://static.uoscholar-server.store}"
NCP_ACCESS_KEY="${NCP_ACCESS_KEY:-TODO_NCP_DEPLOY_ACCESS_KEY}"
NCP_SECRET_KEY="${NCP_SECRET_KEY:-TODO_NCP_DEPLOY_SECRET_KEY}"
NCP_BUCKET="${NCP_BUCKET:-lms-static}"
STATIC_BASE_URL="${STATIC_BASE_URL:-https://static.uoscholar-server.store}"
KMS_ENABLED="${KMS_ENABLED:-true}"
KMS_BASE_URL="${KMS_BASE_URL:-https://ocapi.ncloud.com}"
KMS_KEY_TAG="${KMS_KEY_TAG:-TODO_KMS_KEY_TAG}"
KMS_ACCESS_KEY="${KMS_ACCESS_KEY:-${NCP_ACCESS_KEY}}"
KMS_SECRET_KEY="${KMS_SECRET_KEY:-${NCP_SECRET_KEY}}"
KMS_TOKEN_CREATOR_ID="${KMS_TOKEN_CREATOR_ID:-TODO_KMS_TOKEN_CREATOR_ID}"
EDGE_AUTH_ENABLED="${EDGE_AUTH_ENABLED:-true}"
EDGE_AUTH_KEY="${EDGE_AUTH_KEY:-TODO_EDGE_AUTH_KEY}"
EDGE_AUTH_TOKEN_NAME="${EDGE_AUTH_TOKEN_NAME:-token}"
EDGE_AUTH_DURATION_SECONDS="${EDGE_AUTH_DURATION_SECONDS:-3600}"
VOD_STATION_ENABLED="${VOD_STATION_ENABLED:-true}"
VOD_STATION_CATEGORY_ID="${VOD_STATION_CATEGORY_ID:-21805}"
VOD_STATION_ACCESS_KEY="${VOD_STATION_ACCESS_KEY:-${NCP_ACCESS_KEY}}"
VOD_STATION_SECRET_KEY="${VOD_STATION_SECRET_KEY:-${NCP_SECRET_KEY}}"
DEMO_USER_EMAIL="${DEMO_USER_EMAIL:-demo@lms.local}"
DEMO_USER_PASSWORD="${DEMO_USER_PASSWORD:-demo1234!}"
DEMO_ADMIN_EMAIL="${DEMO_ADMIN_EMAIL:-admin@lms.local}"
DEMO_ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:-admin1234!}"

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
require_value "DB_PASSWORD" "${DB_PASSWORD}"
require_value "JWT_SECRET" "${JWT_SECRET}"
require_value "NCP_ACCESS_KEY" "${NCP_ACCESS_KEY}"
require_value "NCP_SECRET_KEY" "${NCP_SECRET_KEY}"
require_value "KMS_KEY_TAG" "${KMS_KEY_TAG}"
require_value "KMS_TOKEN_CREATOR_ID" "${KMS_TOKEN_CREATOR_ID}"

install_packages() {
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y curl tar ca-certificates rsync
    if ! command -v java >/dev/null 2>&1; then
      apt-get install -y openjdk-17-jre-headless
    fi
    return
  fi
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y curl tar awscli ca-certificates rsync java-17-openjdk-headless
    return
  fi
  if command -v yum >/dev/null 2>&1; then
    yum install -y curl tar awscli ca-certificates rsync java-17-openjdk-headless
    return
  fi
  echo "ERROR: unsupported package manager"
  exit 1
}

ensure_aws_cli() {
  if command -v aws >/dev/null 2>&1; then
    return
  fi

  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get install -y awscli || true
    if command -v aws >/dev/null 2>&1; then
      return
    fi

    apt-get install -y python3-pip python3-venv || apt-get install -y python3-pip python3.12-venv
    python3 -m venv /opt/lms/awscli-venv
    /opt/lms/awscli-venv/bin/pip install --upgrade pip
    /opt/lms/awscli-venv/bin/pip install awscli
    ln -sf /opt/lms/awscli-venv/bin/aws /usr/local/bin/aws
    return
  fi

  echo "ERROR: aws cli install failed"
  exit 1
}

configure_aws_cli_ncp() {
  aws configure set default.region "${NCP_S3_REGION}"
  aws configure set default.s3.signature_version s3v4
  aws configure set default.s3.addressing_style path
  aws configure set default.s3.payload_signing_enabled false
}

install_packages
ensure_aws_cli
configure_aws_cli_ncp

if ! id lms >/dev/null 2>&1; then
  useradd --system --home-dir /opt/lms/backend --shell /sbin/nologin lms || true
fi

mkdir -p /etc/lms /opt/lms/backend /opt/lms/was /var/lib/lms-was
chown -R lms:lms /opt/lms/backend

cat > /etc/lms/bootstrap.env <<EOF
AWS_ACCESS_KEY_ID=${NCP_DEPLOY_ACCESS_KEY}
AWS_SECRET_ACCESS_KEY=${NCP_DEPLOY_SECRET_KEY}
AWS_DEFAULT_REGION=${NCP_S3_REGION}
NCP_S3_ENDPOINT=${NCP_S3_ENDPOINT}
NCP_DEPLOY_BUCKET=${NCP_DEPLOY_BUCKET}
NCP_DEPLOY_PREFIX=${NCP_DEPLOY_PREFIX}
EOF
chmod 600 /etc/lms/bootstrap.env

cat > /opt/lms/backend/.env <<EOF
SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
DB_HOST=${DB_HOST}
DB_PORT=${DB_PORT}
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=${JWT_ACCESS_TOKEN_EXPIRATION_SECONDS}
CORS_ALLOWED_ORIGIN=${CORS_ALLOWED_ORIGIN}
NCP_ACCESS_KEY=${NCP_ACCESS_KEY}
NCP_SECRET_KEY=${NCP_SECRET_KEY}
NCP_S3_ENDPOINT=${NCP_S3_ENDPOINT}
NCP_S3_REGION=${NCP_S3_REGION}
NCP_BUCKET=${NCP_BUCKET}
STATIC_BASE_URL=${STATIC_BASE_URL}
KMS_ENABLED=${KMS_ENABLED}
KMS_BASE_URL=${KMS_BASE_URL}
KMS_KEY_TAG=${KMS_KEY_TAG}
KMS_ACCESS_KEY=${KMS_ACCESS_KEY}
KMS_SECRET_KEY=${KMS_SECRET_KEY}
KMS_TOKEN_CREATOR_ID=${KMS_TOKEN_CREATOR_ID}
EDGE_AUTH_ENABLED=${EDGE_AUTH_ENABLED}
EDGE_AUTH_KEY=${EDGE_AUTH_KEY}
EDGE_AUTH_TOKEN_NAME=${EDGE_AUTH_TOKEN_NAME}
EDGE_AUTH_DURATION_SECONDS=${EDGE_AUTH_DURATION_SECONDS}
VOD_STATION_ENABLED=${VOD_STATION_ENABLED}
VOD_STATION_CATEGORY_ID=${VOD_STATION_CATEGORY_ID}
VOD_STATION_ACCESS_KEY=${VOD_STATION_ACCESS_KEY}
VOD_STATION_SECRET_KEY=${VOD_STATION_SECRET_KEY}
DEMO_USER_EMAIL=${DEMO_USER_EMAIL}
DEMO_USER_PASSWORD=${DEMO_USER_PASSWORD}
DEMO_ADMIN_EMAIL=${DEMO_ADMIN_EMAIL}
DEMO_ADMIN_PASSWORD=${DEMO_ADMIN_PASSWORD}
EOF
chown lms:lms /opt/lms/backend/.env
chmod 600 /opt/lms/backend/.env

cat > /etc/systemd/system/lms-backend.service <<'EOF'
[Unit]
Description=LMS Backend Service
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/lms/backend
EnvironmentFile=/opt/lms/backend/.env
ExecStart=/usr/bin/java -jar /opt/lms/backend/lms-backend.jar
SuccessExitStatus=143
Restart=always
RestartSec=5
User=lms
Group=lms

[Install]
WantedBy=multi-user.target
EOF

cat > /usr/local/bin/lms-was-sync.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

exec 9>/var/lock/lms-was-sync.lock
flock -n 9 || exit 0

set -a
source /etc/lms/bootstrap.env
set +a

VERSION_KEY="${NCP_DEPLOY_PREFIX}/was/latest/version.txt"
ARTIFACT_KEY="${NCP_DEPLOY_PREFIX}/was/latest/lms-was-deploy.tar.gz"
STATE_DIR="/var/lib/lms-was"
LOCAL_VERSION_FILE="${STATE_DIR}/version.txt"
TARGET_JAR="/opt/lms/backend/lms-backend.jar"
TMP_DIR="$(mktemp -d)"
FORCE="${1:-}"
AWS_COMMON_ARGS=(
  --no-cli-pager
  --cli-connect-timeout 5
  --cli-read-timeout 30
  --endpoint-url "${NCP_S3_ENDPOINT}"
  --region "${AWS_DEFAULT_REGION}"
)

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

mkdir -p "${STATE_DIR}" /opt/lms/backend

set +e
remote_version_output="$(aws "${AWS_COMMON_ARGS[@]}" s3 cp "s3://${NCP_DEPLOY_BUCKET}/${VERSION_KEY}" - 2>&1)"
remote_version_status=$?
set -e

remote_version=""
if [[ ${remote_version_status} -eq 0 ]]; then
  remote_version="$(printf '%s' "${remote_version_output}" | tr -d '\r\n[:space:]')"
fi

if [[ -z "${remote_version}" ]]; then
  set +e
  artifact_etag_output="$(aws "${AWS_COMMON_ARGS[@]}" s3api head-object --bucket "${NCP_DEPLOY_BUCKET}" --key "${ARTIFACT_KEY}" --query ETag --output text 2>&1)"
  artifact_etag_status=$?
  set -e

  if [[ ${artifact_etag_status} -ne 0 ]]; then
    echo "ERROR: failed to resolve remote version."
    echo "version.txt output: ${remote_version_output}"
    echo "artifact head output: ${artifact_etag_output}"
    exit 1
  fi

  remote_version="$(printf '%s' "${artifact_etag_output}" | tr -d '\r\n\"[:space:]')"
  if [[ -z "${remote_version}" || "${remote_version}" == "None" ]]; then
    echo "ERROR: remote version and artifact ETag are both empty"
    exit 1
  fi

  echo "WARNING: version.txt missing/empty. using artifact etag as version: ${remote_version}"
fi

local_version=""
if [[ -f "${LOCAL_VERSION_FILE}" ]]; then
  local_version="$(cat "${LOCAL_VERSION_FILE}" | tr -d '\r\n')"
fi

if [[ "${FORCE}" != "--force" && "${local_version}" == "${remote_version}" ]]; then
  echo "lms-was-sync: already latest (${remote_version})"
  exit 0
fi

artifact_path="${TMP_DIR}/lms-was-deploy.tar.gz"
if ! aws "${AWS_COMMON_ARGS[@]}" s3 cp "s3://${NCP_DEPLOY_BUCKET}/${ARTIFACT_KEY}" "${artifact_path}"; then
  echo "ERROR: failed to download artifact from s3://${NCP_DEPLOY_BUCKET}/${ARTIFACT_KEY}"
  exit 1
fi

tar -xzf "${artifact_path}" -C "${TMP_DIR}"

if [[ ! -f "${TMP_DIR}/package/backend/app/lms-backend.jar" ]]; then
  echo "ERROR: invalid artifact layout"
  exit 1
fi

cp -f "${TMP_DIR}/package/backend/app/lms-backend.jar" "${TARGET_JAR}"
chown lms:lms "${TARGET_JAR}"
chmod 644 "${TARGET_JAR}"
echo "${remote_version}" > "${LOCAL_VERSION_FILE}"

systemctl daemon-reload
if systemctl is-active --quiet lms-backend; then
  systemctl restart lms-backend
else
  systemctl start lms-backend
fi
echo "lms-was-sync: deployed ${remote_version}"
EOF
chmod +x /usr/local/bin/lms-was-sync.sh

cat > /etc/systemd/system/lms-was-sync.service <<'EOF'
[Unit]
Description=LMS WAS artifact sync
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/lms-was-sync.sh
TimeoutStartSec=120
TimeoutStopSec=20
EOF

cat > /etc/systemd/system/lms-was-sync.timer <<EOF
[Unit]
Description=LMS WAS artifact sync timer

[Timer]
OnBootSec=45s
OnUnitActiveSec=${SYNC_INTERVAL_MINUTES}min
Unit=lms-was-sync.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable lms-backend

if /usr/local/bin/lms-was-sync.sh --force; then
  echo "lms-was-init: initial sync succeeded"
else
  echo "WARNING: initial sync failed; timer will retry automatically"
fi
systemctl enable --now lms-was-sync.timer

echo "lms-was-init: completed"
