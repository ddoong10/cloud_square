# 경기도교통연수원 LMS (NCP 기반 코드 구현)

인프라는 이미 구축되어 있다는 전제에서, 아래 코드만 포함합니다.
- `backend/`: Spring Boot(Java) REST API
- `frontend/`: 정적 HTML/CSS/JS

## 1. 디렉터리 구조

```text
cloud_square/
├─ backend/
│  ├─ src/main/java/com/uos/lms
│  │  ├─ auth      # /api/auth/login
│  │  ├─ health    # /, /health
│  │  ├─ lecture   # /api/lectures
│  │  ├─ security  # JWT 필터/보안 설정
│  │  ├─ upload    # /api/uploads (Object Storage 업로드)
│  │  └─ user      # /api/me, 데모 유저 시드
│  ├─ src/main/resources
│  │  └─ application.yml
│  └─ .env.example
└─ frontend/
   ├─ index.html
   ├─ style.css
   ├─ app.js
   └─ config.js
```

## 2. 백엔드 주요 기능

- JWT(HS256) Access Token 인증
- 무상태(Stateless) 구조, 세션/Redis 미사용
- 사용자 역할(`USER`/`ADMIN`) 기반 인가
- NCP KMS 기반 봉투암호화 적용
  - 강의 `videoUrl` DB 저장 시 암호문 저장, 조회 시 복호화
  - 강의 `thumbnailUrl`은 비민감 데이터로 평문 저장
  - 회원가입 `residentNumber` DB 저장 시 암호문 저장(평문 미저장)
- CORS 허용 Origin 기본값:
  - `https://lms.uoscholar-server.store`
- 헬스 체크:
  - `GET /` -> `200 OK` + `OK`
  - `GET /health` -> `200 OK`
- DB 연동(MySQL, JPA `ddl-auto=update`)
- 강의 메타데이터는 DB 저장, 영상 파일 원본은 Object Storage/CDN 사용
- 관리자 요청 시 `uploads/` 경로 Object Storage를 스캔해 DB 강의 목록과 동기화
- 업로드:
  - `POST /api/uploads` multipart 파일을 NCP Object Storage(S3 호환)로 업로드
  - 응답 URL은 `https://static.uoscholar-server.store/<key>` 형태
  - 서버 로컬 디스크 저장 로직 없음

## 3. API 목록

- `POST /api/auth/login`
  - Body: `{ "email": "...", "password": "..." }`
  - Response: `{ "tokenType": "Bearer", "accessToken": "...", "expiresInSeconds": 3600, "role": "USER|ADMIN" }`
- `POST /api/auth/signup`
  - Body: `{ "email": "...", "password": "8자 이상...", "residentNumber": "901010-1234567" }`
  - 가입 계정은 항상 `USER` 권한으로 생성
  - 주민등록번호는 KMS 봉투암호화 후 DB 저장
- `GET /api/me/resident-number` (JWT 필요)
  - 로그인 사용자의 주민번호 복호화 조회
  - Response: `{ "userId": ..., "residentNumber": "...", "encryptedAtRest": true|false, "available": true|false }`

- `GET /api/me` (JWT 필요)
- `GET /api/lectures` (JWT 필요)
- `POST /api/uploads` (JWT 필요, `ADMIN`만 가능, multipart `file`)
- `POST /api/lectures` (JWT 필요, `ADMIN`만 가능)
  - Body: `{ "title": "...", "videoUrl": "https://static.uoscholar-server.store/...", "thumbnailUrl": "https://static.uoscholar-server.store/...(optional)" }`
- `GET /api/lectures/{lectureId}/crypto-check` (JWT 필요, `ADMIN`만 가능)
  - 강의 URL 암복호화 점검
  - Response: `{ "lectureId": ..., "videoEncryptedAtRest": true|false, "videoDecryptionOk": true|false, ... }`
- `DELETE /api/lectures/{lectureId}` (JWT 필요, `ADMIN`만 가능)
  - DB 강의 삭제
  - `videoUrl`이 `STATIC_BASE_URL` 하위 경로면 Object Storage 삭제도 함께 시도
  - `thumbnailUrl`도 `STATIC_BASE_URL` 하위 경로면 삭제 시도
  - Response: `{ "lectureId": ..., "objectDeleteAttempted": true|false, "objectDeleted": true|false, "thumbnailDeleteAttempted": true|false, "thumbnailDeleted": true|false }`
- `POST /api/lectures/sync` (JWT 필요, `ADMIN`만 가능)
  - Object Storage(`uploads/`)의 **영상 파일만** DB 강의 목록으로 수동 동기화
  - 이미지 파일(썸네일)은 동기화 대상에서 제외
  - Response: `{ "scannedCount": ..., "insertedCount": ... }`

데모 계정(초기 자동 생성):
- 일반 사용자: `demo@lms.local` / `demo1234!`
- 관리자: `admin@lms.local` / `admin1234!`

## 4. 환경변수 설정

`backend/.env.example`를 참고해 실행 환경변수를 설정합니다.

필수 키:
- `SPRING_PROFILES_ACTIVE=prod|dev`
- `DB_HOST=TODO_DB_HOST`
- `DB_PORT=3306`
- `DB_NAME=lms-db`
- `DB_USER=user`
- `DB_PASSWORD=...`
- `JWT_SECRET=...` (긴 랜덤 문자열 권장)
- `NCP_ACCESS_KEY=...`
- `NCP_SECRET_KEY=...`
- `NCP_S3_ENDPOINT=...`
- `NCP_S3_REGION=kr-standard`
- `NCP_BUCKET=lms-static`
- `KMS_ENABLED=true|false`
- `KMS_BASE_URL=https://ocapi.ncloud.com`
- `KMS_KEY_TAG=...`
- `KMS_ACCESS_KEY=...`
- `KMS_SECRET_KEY=...`
- `KMS_TOKEN_CREATOR_ID=...` (현재는 저장만, 토큰 모드 연동은 TODO)
- `STATIC_BASE_URL=https://static.uoscholar-server.store`
- `DEMO_USER_EMAIL=demo@lms.local`
- `DEMO_USER_PASSWORD=demo1234!`
- `DEMO_ADMIN_EMAIL=admin@lms.local`
- `DEMO_ADMIN_PASSWORD=admin1234!`

참고:
- 개발용 로컬 Origin은 현재 TODO(`CORS_ALLOWED_ORIGIN`로 조정 가능).

## 5. 로컬 실행 (VPN 전제)

Cloud DB가 private endpoint이므로 VPN 연결이 선행되어야 합니다.

1. SSL VPN 접속
   - Split Routing 대상에 `10.173.0.0/16` 포함 확인
2. 환경변수 주입
3. 백엔드 실행

```bash
cd backend
chmod +x gradlew
./gradlew bootRun
```

기본 포트는 `8080`입니다.

헬스 체크:

```bash
curl http://localhost:8080/
curl http://localhost:8080/health
```

## 6. 프론트 실행

`frontend/config.js`를 배포 환경에 맞게 수정 후 정적 파일로 서빙합니다.

현재 기본값:
- `API_BASE_URL=https://lms.uoscholar-server.store`
- `STATIC_BASE_URL=https://static.uoscholar-server.store`

로컬 테스트 예시(간단 정적 서버):

```bash
cd frontend
python3 -m http.server 5500
```

브라우저에서 `http://localhost:5500` 접속.

동작 요약:
- 첫 화면은 로그인/회원가입 화면입니다.
- 로그인 후 강의 목록 화면이 열립니다.
- 강의 목록의 `강의 보기`를 누르면 `static` 영상 URL로 이동합니다.
- `ADMIN` 로그인 시 관리자 패널(제목+파일 한 번 제출 + CDN 동기화 버튼)이 표시됩니다.
- `ADMIN`은 썸네일 이미지를 선택적으로 함께 업로드할 수 있습니다.
- `ADMIN`은 강의 목록에서 `삭제` 버튼으로 강의를 제거할 수 있습니다.
- `ADMIN`은 강의 목록에서 `암복호화 점검`으로 video URL 저장 암복호화 상태를 확인할 수 있습니다.
- 일반 사용자는 `내 주민번호 확인` 버튼으로 본인 주민번호 복호화 결과를 확인할 수 있습니다.

## 7. TODO

- 실제 사용자/권한 모델 확정 후 로그인 정책 대체(현재는 데모 계정 기반)
- Refresh Token 도입
- 프론트 정적 파일의 WEB 서버 배포 절차 문서화

## 8. 자동배포 (WEB/WAS Auto Scaling)

결론: **프론트(WEB)와 백엔드(WAS)는 분리 배포**가 맞습니다.

- WEB ASG: `frontend/` 정적 파일만 배포
- WAS ASG: `backend` jar만 배포
- 두 그룹을 같은 아티팩트로 배포하지 않고, 각각 독립 아티팩트/검증 경로를 사용

### 8-1. 레포에 추가된 자동배포 파일

- SourceBuild 설정: `buildspec.yml`
- 패키징 스크립트: `scripts/package-deploy.sh`
- WEB 배포 번들 템플릿
  - `deploy/web/appspec.yml`
  - `deploy/web/scripts/before_install.sh`
  - `deploy/web/scripts/after_install.sh`
  - `deploy/web/scripts/validate_service.sh`
- WAS 배포 번들 템플릿
  - `deploy/was/appspec.yml`
  - `deploy/was/scripts/before_install.sh`
  - `deploy/was/scripts/after_install.sh`
  - `deploy/was/scripts/validate_service.sh`
  - `deploy/was/lms-backend.service.example` (systemd 예시)

### 8-2. 배포 아티팩트 생성

로컬/빌드 서버에서 아래 1개 명령으로 두 아티팩트를 같이 생성합니다.

```bash
./scripts/package-deploy.sh
```

생성물:
- `dist/lms-web-deploy.tar.gz`
- `dist/lms-was-deploy.tar.gz`

### 8-3. NCP 파이프라인 권장 구성

1. SourceBuild 단계에서 `buildspec.yml` 실행
2. WEB SourceDeploy 단계
3. WAS SourceDeploy 단계

각 단계 타겟:
- WEB 단계는 WEB ASG만 타겟
- WAS 단계는 WAS ASG만 타겟

배포 검증:
- WEB: `curl -f http://127.0.0.1/`
- WAS: `curl -f http://127.0.0.1:8080/health`

### 8-4. 인스턴스 사전 준비

- 공통:
  - SourceDeploy Agent 설치 (Launch Template/Init Script에도 반영)
- WEB:
  - nginx 설치
  - 정적 배포 루트 준비 (`WEB_ROOT`, 기본 `/var/www/lms`)
- WAS:
  - Java 17 설치
  - `lms-backend` systemd 서비스 생성
  - 런타임 환경파일 준비 (`WAS_ENV_FILE`, 기본 `/opt/lms/backend/.env`)

`WAS` 환경파일(`.env`) 필수:
- `JWT_SECRET`
- `DB_*`
- `NCP_*`
- `KMS_*` (KMS 사용 시)

### 8-5. 운영 주의사항

- Auto Scaling 확장 인스턴스도 동일 준비 상태여야 하므로 Launch Template에 Agent/런타임 의존성 반영
- WEB/WAS 배포를 분리하지 않으면 롤백/헬스체크 원인 파악이 어려워짐
- 배포 실패 시 해당 단계(SourceDeploy)에서 즉시 롤백

## 9. GitHub Actions + Init Script 방식 (SourcePipeline 미사용)

`git push`만으로 배포 아티팩트를 Object Storage에 올리고, 각 인스턴스는 부팅 후 자동으로 최신 버전을 동기화해 기동할 수 있습니다.

추가된 파일:
- GitHub Actions: `.github/workflows/publish-deploy-artifacts.yml`
- WEB init script: `ops/bootstrap/web-init.sh`
- WAS init script: `ops/bootstrap/was-init.sh`
- WEB 최종본(UserData 복붙용): `ops/bootstrap/web-init.final.sh`
- WAS 최종본(UserData 복붙용): `ops/bootstrap/was-init.final.sh`
- 입력값 정리: `ops/bootstrap/required-values.md`

### 9-1. GitHub Actions Secrets

저장소 `Settings > Secrets and variables > Actions`에 아래 키를 추가합니다.

- `NCP_DEPLOY_ACCESS_KEY`
- `NCP_DEPLOY_SECRET_KEY`
- `NCP_S3_ENDPOINT` (예: `https://kr.object.ncloudstorage.com`)
- `NCP_S3_REGION` (예: `kr-standard`)
- `NCP_DEPLOY_BUCKET`
- `NCP_DEPLOY_PREFIX` (없으면 `uploads/lms-deploy`)

워크플로 동작:
1. `scripts/package-deploy.sh` 실행
2. `dist/lms-web-deploy.tar.gz`, `dist/lms-was-deploy.tar.gz` 생성
3. AWS SDK for JavaScript `2.348.0` 기반으로 Object Storage `latest/` + `releases/<sha>/` 경로 업로드

### 9-2. Launch Template UserData 적용

WEB ASG Launch Template:
- `ops/bootstrap/web-init.final.sh` 내용을 UserData로 넣습니다.
- 현재 기본 `WAS_UPSTREAM_HOST`는 `TODO_INTERNAL_LB_HOST`으로 반영되어 있습니다.

WAS ASG Launch Template:
- `ops/bootstrap/was-init.final.sh` 내용을 UserData로 넣습니다.

핵심:
- 새 인스턴스가 뜨면 init script가 자동 실행됨
- 최초 부팅 시 최신 아티팩트를 내려받아 즉시 서비스 기동
- 이후 systemd timer가 주기적으로 새 버전 존재 여부를 확인해 자동 반영

### 9-3. WEB 보안(nginx rate limit)

`web-init.sh`는 nginx에 기본 rate limit/connection limit 설정을 적용합니다.

- 기본값:
  - `WEB_RATE_LIMIT_RPS=20r/s`
  - `WEB_RATE_LIMIT_BURST=60`
  - `WEB_CONN_LIMIT=50`
- 조정은 `web-init.sh` 상단 변수 변경으로 가능

### 9-4. 서버 직접 접속(Putty) 필요 여부

- 정상적으로 init script가 들어간 Launch Template로 ASG를 교체하면, 원칙적으로 일상 배포에 Putty는 필요 없습니다.
- Putty는 초기 1회 점검(로그 확인/네트워크 검증) 용도로만 사용하는 것을 권장합니다.
