# BuildUp API 연동 가이드

이 문서는 프론트엔드 개발자가 BuildUp 백엔드와 바로 연동할 수 있도록 현재 구현된 HTTP 계약만 정리합니다. 프로젝트 소개는 `README.md`, 변경 이력은 `CHANGELOG.md`, 개발 규칙은 `AGENTS.md`와 `docs/COLLABORATION.md`를 참고하세요.

## 1. 기본 정보

| 환경 | Base URL |
| --- | --- |
| 로컬 | `http://localhost:8080` |
| 운영 | 배포 후 확정된 HTTPS 주소를 여기에 기록 |

- 요청과 응답의 기본 형식은 JSON입니다.
- 날짜는 `YYYY-MM-DD`, 날짜·시간은 ISO 8601 형식입니다.
- 현재 프로젝트에는 Swagger/OpenAPI 의존성이 없으므로 `/swagger-ui` 주소를 전제로 연동하지 않습니다.
- 공개 API는 `GET /api/health`와 `/api/auth/*`입니다. 나머지 API에는 Access Token이 필요합니다.

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

## 2. 인증 흐름

### 회원가입

`POST /api/auth/signup` → `201 Created`

```json
{
  "name": "홍길동",
  "email": "user@example.com",
  "phone": "010-1234-5678",
  "password": "Strong!Pass1"
}
```

- 비밀번호: 10~72자, 대문자·소문자·숫자·특수문자를 각각 1개 이상 포함
- `phone`은 선택값이며 최대 30자입니다.
- 역할은 서버가 `USER`로 지정하며 클라이언트가 선택하지 않습니다.

### 로그인

`POST /api/auth/login` → `200 OK`

```json
{
  "email": "user@example.com",
  "password": "Strong!Pass1"
}
```

회원가입과 로그인은 다음 형태의 토큰 응답을 반환합니다.

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "refreshToken": "...",
  "accessTokenExpiresAt": "2026-09-30T01:15:00Z",
  "refreshTokenExpiresAt": "2026-10-14T01:00:00Z"
}
```

- Access Token 기본 수명: 15분
- Refresh Token 기본 수명: 14일
- Access Token은 보호 API의 `Authorization` 헤더에 사용합니다.
- Refresh Token은 토큰 재발급과 로그아웃에만 사용하고 로그·URL·Git에 남기지 않습니다.

### 토큰 재발급

`POST /api/auth/refresh` → `200 OK`

```json
{ "refreshToken": "기존-refresh-token" }
```

응답은 로그인과 같은 토큰 묶음입니다. 재발급할 때 Refresh Token도 교체되므로, 성공 즉시 Access Token과 Refresh Token을 모두 새 값으로 덮어써야 합니다. 이미 사용한 Refresh Token을 다시 사용하면 거부됩니다. 동시에 여러 재발급 요청을 보내지 마세요.

### 로그아웃

`POST /api/auth/logout` → `204 No Content`

```json
{ "refreshToken": "현재-refresh-token" }
```

성공 여부와 관계없이 프론트엔드는 보관 중인 두 토큰을 삭제합니다.

### 현재 사용자

`GET /api/users/me`

```json
{
  "id": 1,
  "name": "홍길동",
  "email": "user@example.com",
  "phone": "010-1234-5678",
  "role": "USER"
}
```

## 3. 엔드포인트 목록

아래 표에서 `인증`은 Access Token 필요 여부입니다.

### 상태 확인

| Method | Path | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/health` | 불필요 | 서버 상태 확인 |

정상 응답: `{"status":"ok","message":"BuildUp API is running"}`

### 현장과 멤버

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/sites` | 현장 생성, 생성자는 `OWNER` |
| POST | `/api/sites/join` | 참여 코드로 현장 가입 |
| GET | `/api/sites` | 내가 활성 멤버인 현장 목록 |
| GET | `/api/sites/{siteId}` | 현장 상세와 공정 목록 |
| GET | `/api/sites/{siteId}/members` | 멤버 목록 조회 |
| GET | `/api/sites/{siteId}/join-code` | 참여 코드 조회, `OWNER` 전용 |
| PATCH | `/api/sites/{siteId}/members/{membershipId}/role` | 멤버 역할 변경, `OWNER` 전용 |

주요 요청 예시:

```json
// POST /api/sites
{ "name": "강남 현장", "address": "서울시 강남구", "companyId": null }

// POST /api/sites/join
{ "code": "JOIN-CODE" }

// PATCH .../role
{ "role": "MANAGER" }
```

현장 역할은 `OWNER`, `MANAGER`, `WORKER`, `VIEWER`입니다. 멤버 상태는 `ACTIVE`, `INACTIVE`, `LEFT`입니다.

### 공정

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/sites/{siteId}/processes` | 공정 목록 |
| POST | `/api/sites/{siteId}/processes` | 공정 생성, 관리자 권한 필요 |
| PUT | `/api/sites/{siteId}/processes/{processId}` | 공정 수정, 관리자 권한 필요 |

```json
// POST 요청 예시
{
  "processKey": "FOUNDATION",
  "name": "기초 공사",
  "weight": 20.00,
  "planStart": "2026-10-01",
  "planEnd": "2026-10-10",
  "sortOrder": 1
}
```

`PUT` 요청에는 `processKey`를 제외한 `name`, `weight`, `planStart`, `planEnd`, `sortOrder`를 모두 보냅니다. 진행률은 승인된 보고서를 통해 갱신되며 이 API에서 직접 수정하지 않습니다.

### 보고서

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/sites/{siteId}/reports` | 보고서 작성, `VIEWER` 제외 |
| GET | `/api/sites/{siteId}/reports` | 보고서 목록 |
| GET | `/api/reports/{reportId}` | 보고서 상세 |
| POST | `/api/reports/{reportId}/approve` | 승인, 관리자 권한 필요 |
| POST | `/api/reports/{reportId}/reject` | 반려, 관리자 권한 필요 |

목록 쿼리: `processKey`, `reviewStatus`, `page=0`, `size=20`을 사용할 수 있습니다. `size`는 1~100이며 `reviewStatus`는 `PENDING`, `APPROVED`, `REJECTED`입니다.

```json
// POST /api/sites/{siteId}/reports
{
  "processKey": "FOUNDATION",
  "toProgress": 35.50,
  "memo": "기초 타설 완료",
  "weather": "맑음",
  "workers": 12,
  "equipment": "굴착기 1대",
  "photoFileNames": ["foundation-01.jpg"],
  "files": [
    { "path": "reports/file.pdf", "originalName": "작업일보.pdf", "sizeBytes": 12345 }
  ]
}

// 승인: comment는 선택
{ "comment": "확인했습니다." }

// 반려: comment는 필수
{ "comment": "사진을 추가해 주세요." }
```

현재 파일 API는 업로드 자체가 아니라 파일 경로·이름·크기 메타데이터를 받습니다. 실제 업로드 방식이 확정되기 전 임의의 업로드 URL을 가정하지 마세요.

### 자재 요청

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/sites/{siteId}/material-requests` | 자재 요청 생성, `VIEWER` 제외 |
| GET | `/api/sites/{siteId}/material-requests` | 자재 요청 목록 |
| GET | `/api/material-requests/{requestId}` | 자재 요청 상세 |
| PATCH | `/api/material-requests/{requestId}/status` | 상태 변경, 관리자 권한 필요 |

목록 쿼리: `processKey`, `status`, `page=0`, `size=20`을 사용할 수 있습니다. 상태 흐름은 `REQUESTED → APPROVED → ORDERED → DELIVERED` 또는 `REQUESTED → REJECTED`입니다.

```json
// 생성
{
  "processKey": "FOUNDATION",
  "neededBy": "2026-10-05",
  "urgent": true,
  "note": "오전 입고 요청",
  "items": [
    { "name": "시멘트", "quantity": 20.000, "unit": "포" }
  ]
}

// 상태 변경
{ "status": "REJECTED", "rejectReason": "재고 확인 필요" }
```

`REJECTED`일 때 `rejectReason`은 필수이고, 다른 상태에서는 보내지 않습니다.

### 알림과 디바이스 토큰

| Method | Path | 성공 응답 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/notifications` | 알림 배열 | 내 알림 목록 |
| GET | `/api/notifications/unread-count` | `{"unreadCount":3}` | 읽지 않은 개수 |
| PATCH | `/api/notifications/{notificationId}/read` | 알림 객체 | 한 건 읽음 처리 |
| PATCH | `/api/notifications/read-all` | `{"updatedCount":3}` | 모두 읽음 처리 |
| POST | `/api/devices/tokens` | `204` | FCM 등 디바이스 토큰 등록 |
| DELETE | `/api/devices/tokens` | `204` | 디바이스 토큰 삭제 |

디바이스 토큰 요청: `{"token":"device-token-value"}`

## 4. 페이징과 오류 처리

보고서·자재 요청 목록의 공통 응답 형태:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

애플리케이션 오류의 기본 형태:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-09-30T01:00:00Z",
  "fieldErrors": {
    "email": "must be a well-formed email address"
  }
}
```

- `400`: 입력값 또는 허용되지 않은 상태 전이
- `401`: 로그인 실패, Access Token 없음·만료·위조
- `403`: 해당 현장 또는 기능에 대한 권한 없음
- `404`: 리소스를 찾을 수 없거나 접근 가능한 범위에 없음
- `409`: 이메일, 참여, 공정 키 등 중복·충돌

프론트엔드는 화면에 `message`를 그대로 노출하기보다 `code`를 기준으로 사용자 메시지를 매핑하고, `fieldErrors`가 있으면 입력 필드 옆에 표시합니다.

## 5. 프론트엔드 연동 체크리스트

1. 환경별 Base URL을 프론트엔드 환경변수 한 곳에서 관리합니다.
2. 로그인 후 Access Token을 요청 헤더에 자동 첨부합니다.
3. `401`과 Access Token 만료 시 재발급은 한 번만 실행하고, 새 토큰으로 원 요청을 한 번 재시도합니다.
4. 재발급 실패 시 토큰을 삭제하고 로그인 화면으로 이동합니다.
5. 운영 주소 확정 후 백엔드의 `BUILDUP_CORS_ALLOWED_ORIGINS`에 프론트엔드 Origin을 등록합니다.
6. DB 주소·계정·비밀번호와 JWT Secret은 프론트엔드에 전달하지 않습니다.
