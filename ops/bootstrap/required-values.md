# Init Script Required Values

아래 값이 있어야 `ops/bootstrap/web-init.sh`, `ops/bootstrap/was-init.sh`가 정상 동작합니다.

## 공통 (WEB/WAS)

- `NCP_DEPLOY_ACCESS_KEY`: GitHub Actions가 업로드한 배포 아티팩트를 읽을 수 있는 키
- `NCP_DEPLOY_SECRET_KEY`
- `NCP_S3_ENDPOINT`: 예) `https://kr.object.ncloudstorage.com`
- `NCP_S3_REGION`: 예) `kr-standard`
- `NCP_DEPLOY_BUCKET`: 배포 아티팩트 저장 버킷
- `NCP_DEPLOY_PREFIX`: 기본 `uploads/lms-deploy`

## WEB 전용

- `WAS_UPSTREAM_HOST`: WEB가 `/api/*`를 프록시할 Internal LB 주소
- `WAS_UPSTREAM_PORT`: 기본 `80`

## WAS 전용

- `DB_PASSWORD`
- `JWT_SECRET`
- `NCP_ACCESS_KEY`, `NCP_SECRET_KEY`
- `KMS_KEY_TAG`
- `KMS_TOKEN_CREATOR_ID`

참고:
- WAS는 `/opt/lms/backend/.env`를 init script에서 생성합니다.
- 민감정보를 UserData에 직접 넣기 싫으면, 별도 비밀 저장소/암호화 파일에서 부팅 시 주입하도록 변경하세요(TODO).
