# 경기도교통연수원 LMS

온라인 학습관리시스템 (Learning Management System)

## 시스템 아키텍처

```
                         ┌─────────────────────────────────────────────┐
                         │              NCP (Naver Cloud)              │
   사용자 브라우저        │                                             │
  ┌──────────────┐       │  ┌───────────┐        ┌──────────────────┐  │
  │              │ HTML/  │  │ CDN+      │        │ Object Storage   │  │
  │  Frontend    │◄──────►│  │ (Static)  │◄───────│ (lms-static)     │  │
  │  SPA         │ JS/CSS │  │           │        │  ├─ *.html/js/css│  │
  │              │        │  └───────────┘        │  ├─ thumbnails/  │  │
  └──────┬───────┘        │                       │  └─ uploads/     │  │
         │                │  ┌───────────┐        └──────────────────┘  │
         │ REST API       │  │ CDN+      │        ┌──────────────────┐  │
         │ (JWT Bearer)   │  │ (VOD)     │◄───────│ VOD Station      │  │
         │                │  │           │  HLS   │ (HLS 트랜스코딩) │  │
         │                │  └───────────┘        └────────┬─────────┘  │
         │                │                                │            │
         │                │  ┌───────────┐        ┌────────┴─────────┐  │
         └───────────────►│  │ WAS       │        │ Object Storage   │  │
                          │  │ (Spring   │───────►│ (VOD Input)      │  │
                          │  │  Boot)    │ upload │ └─ videos/       │  │
                          │  └─────┬─────┘        └──────────────────┘  │
                          │        │                                    │
                          │  ┌─────┴─────┐                              │
                          │  │ Cloud DB  │                              │
                          │  │ (MySQL)   │                              │
                          │  └───────────┘                              │
                          └─────────────────────────────────────────────┘
```

## 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Java 17, Spring Boot 3.4.2, Spring Security, Spring Data JPA |
| **Frontend** | Vanilla JS SPA (Hash Router), Video.js 8.10.0 |
| **Database** | MySQL (Cloud DB for MySQL) |
| **인증** | JWT (HS256), Stateless |
| **스토리지** | NCP Object Storage (S3 호환) |
| **영상** | NCP VOD Station (HLS 트랜스코딩) + CDN |
| **정적 파일** | NCP CDN+ |
| **PDF** | Apache PDFBox 3.0.3, NanumGothic 한글 폰트 |
| **QR 코드** | Google ZXing |
| **암호화** | NCP KMS 봉투암호화 (videoUrl 암호화 저장) |
| **CI/CD** | GitHub Actions → NCP Object Storage → WAS Init Script (2분 주기 동기화) |

## 디렉터리 구조

```
cloud_square/
├── backend/
│   └── src/main/java/com/uos/lms/
│       ├── auth/           # 로그인, 회원가입
│       ├── certificate/    # 이수증 발급, PDF 생성, QR 코드
│       ├── common/         # 공통 예외 처리
│       ├── config/         # S3, Storage, CORS 설정
│       ├── course/         # 과정 CRUD, 공개/비공개
│       ├── enrollment/     # 수강 신청, 진도율, 수료 판정
│       ├── health/         # 헬스 체크 (/, /health)
│       ├── kms/            # NCP KMS 봉투암호화
│       ├── lecture/        # 강의 CRUD, CDN 동기화
│       ├── progress/       # 강의별 진도 추적
│       ├── security/       # JWT 필터, SecurityConfig
│       ├── upload/         # Object Storage 파일 업로드
│       └── user/           # 사용자 관리, 관리자 통계
│
├── frontend/
│   ├── index.html          # SPA 진입점
│   ├── app.js              # 라우터 초기화
│   ├── router.js           # Hash 기반 SPA 라우터
│   ├── api.js              # API 클라이언트 (JWT 자동 첨부)
│   ├── components.js       # 공통 UI 컴포넌트
│   ├── config.js           # API/CDN URL 설정
│   ├── style.css           # 전체 스타일 (반응형 포함)
│   ├── pages/
│   │   ├── auth.js         # 로그인 / 회원가입
│   │   ├── home.js         # 메인 페이지
│   │   ├── courses.js      # 과정 목록
│   │   ├── course-detail.js# 과정 상세 (수강 신청)
│   │   ├── learn.js        # 강의실 (HLS 플레이어, 진도 추적)
│   │   ├── my-learning.js  # 내 학습 현황
│   │   ├── my-certificates.js # 내 이수증
│   │   ├── profile.js      # 프로필 (이름/비밀번호 변경)
│   │   └── verify.js       # 이수증 진위 확인
│   └── pages/admin/
│       ├── dashboard.js    # 관리자 대시보드 (통계)
│       ├── courses.js      # 과정 관리 (CRUD)
│       ├── lectures.js     # 강의 관리 (CRUD, CDN 동기화)
│       ├── users.js        # 사용자 관리
│       └── certificates.js # 이수증 관리
│
├── deploy/                 # 배포 스크립트 (WEB/WAS appspec)
├── ops/                    # 인프라 부트스트랩 (init script, nginx)
├── scripts/                # 빌드/업로드 스크립트
└── .github/workflows/      # GitHub Actions CI/CD
```

## 주요 기능

### 학습자
- 회원가입 / 로그인 (JWT)
- 과정 목록 조회 및 수강 신청
- HLS 영상 시청 (Video.js, seek 제한으로 건너뛰기 방지)
- 강의별 진도율 자동 추적 (5초 간격 서버 저장)
- 과정 수료 시 이수증 발급 및 PDF 다운로드
- QR 코드를 통한 이수증 진위 확인
- 프로필 관리 (이름 변경, 비밀번호 변경)

### 관리자
- 대시보드 (총 사용자, 과정, 수강, 수료율, 이수증 통계)
- 과정 생성 / 수정 / 삭제 / 공개 설정
- 강의 생성 / 수정 / 삭제 (영상 업로드 → VOD Station 자동 HLS 변환)
- CDN 동기화 (Object Storage 스캔 → DB 반영)
- 사용자 / 이수증 관리

## API 엔드포인트

### 인증
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 → JWT 발급 |

### 사용자
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/api/me` | USER | 내 정보 |
| PUT | `/api/me/name` | USER | 이름 변경 |
| PUT | `/api/me/password` | USER | 비밀번호 변경 (204) |
| GET | `/api/admin/stats` | ADMIN | 관리자 통계 |

### 과정
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/api/courses` | USER | 과정 목록 |
| GET | `/api/courses/{id}` | USER | 과정 상세 |
| POST | `/api/courses` | ADMIN | 과정 생성 |
| PUT | `/api/courses/{id}` | ADMIN | 과정 수정 |
| DELETE | `/api/courses/{id}` | ADMIN | 과정 삭제 |

### 강의
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/api/lectures` | USER | 강의 목록 |
| POST | `/api/lectures` | ADMIN | 강의 생성 |
| PUT | `/api/lectures/{id}` | ADMIN | 강의 수정 |
| DELETE | `/api/lectures/{id}` | ADMIN | 강의 삭제 |
| POST | `/api/lectures/sync` | ADMIN | CDN 동기화 |
| GET | `/api/lectures/{id}/stream-url` | USER | HLS 스트림 URL (서명 포함, 10분 만료) |

### 수강
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/courses/{id}/enroll` | USER | 수강 신청 |
| GET | `/api/my-enrollments` | USER | 내 수강 목록 |

### 진도
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/progress` | USER | 진도 업데이트 |
| GET | `/api/progress/lecture/{id}` | USER | 강의별 진도 조회 |

### 이수증
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/courses/{id}/certificate` | USER | 이수증 발급 |
| GET | `/api/my-certificates` | USER | 내 이수증 목록 |
| GET | `/api/certificates/{number}/pdf` | USER | PDF 다운로드 |
| GET | `/api/certificates/{number}/verify` | 공개 | 진위 확인 |

### 업로드
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/uploads` | ADMIN | 정적 파일 업로드 |
| POST | `/api/uploads/vod` | ADMIN | 영상 업로드 (VOD Station) |

## 배포

### CI/CD 흐름

```
git push main
    └─► GitHub Actions
         ├─ Backend: gradlew bootJar → tar.gz → Object Storage
         ├─ Frontend: frontend/* → CDN Object Storage (lms-static)
         └─ WAS: Init Script가 2분 주기로 최신 JAR 감지 → 자동 배포
```

### GitHub Actions Secrets

| Secret | 설명 |
|--------|------|
| `NCP_DEPLOY_ACCESS_KEY` | Object Storage 접근 키 |
| `NCP_DEPLOY_SECRET_KEY` | Object Storage 비밀 키 |
| `NCP_S3_ENDPOINT` | `https://kr.object.ncloudstorage.com` |
| `NCP_S3_REGION` | `kr-standard` |
| `NCP_DEPLOY_BUCKET` | WAS 배포 아티팩트 버킷 |
| `NCP_DEPLOY_PREFIX` | WAS 배포 경로 prefix |
| `NCP_CDN_BUCKET` | CDN 정적 파일 버킷 (`lms-static`) |
| `NCP_CDN_PREFIX` | CDN 경로 prefix (빈 값 = 루트) |

### 프론트엔드 배포 후 CDN 캐시 퍼지 필요

프론트엔드 변경 시 GitHub Actions 완료 후 **NCP CDN 캐시 퍼지**를 수행해야 합니다.

## 로컬 개발

Cloud DB가 Private Subnet에 있으므로 **VPN 연결**이 선행되어야 합니다.

```bash
# 백엔드
cd backend
cp .env.example .env   # 환경변수 설정
chmod +x gradlew
./gradlew bootRun       # http://localhost:8080

# 프론트엔드
cd frontend
python3 -m http.server 5500  # http://localhost:5500
```

### 데모 계정

| 역할 | 이메일 | 비밀번호 |
|------|--------|----------|
| 사용자 | `demo@lms.local` | `demo1234!` |
| 관리자 | `admin@lms.local` | `admin1234!` |

## 환경변수

`backend/.env.example` 참고. 주요 항목:

| 변수 | 설명 |
|------|------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | MySQL 접속 정보 |
| `JWT_SECRET` | JWT 서명 키 |
| `NCP_ACCESS_KEY`, `NCP_SECRET_KEY` | NCP 인증 |
| `NCP_S3_ENDPOINT`, `NCP_S3_REGION`, `NCP_BUCKET` | Object Storage |
| `STATIC_BASE_URL` | 정적 CDN URL (`https://static.uoscholar-server.store`) |
| `VOD_CDN_BASE_URL` | VOD CDN URL |
| `VOD_BUCKET_ENC_NAME` | VOD Station 버킷 암호화명 |
| `VOD_INPUT_BUCKET` | VOD 원본 업로드 버킷 |
| `KMS_ENABLED`, `KMS_*` | NCP KMS 봉투암호화 설정 |
