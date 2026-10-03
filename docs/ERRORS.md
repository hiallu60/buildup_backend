# API 오류 규격

이 문서는 BuildUp API의 공통 오류 응답 계약을 정의합니다. 프론트엔드는 표시 문구보다 `code`를 기준으로 분기합니다.

## 공통 응답

```json
{
  "timestamp": "2026-10-03T03:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/auth/signup",
  "fieldErrors": {
    "email": "must be a well-formed email address"
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `timestamp` | 서버가 오류 응답을 만든 UTC 시각 |
| `status` | HTTP 상태 코드와 같은 숫자 |
| `code` | 클라이언트 분기에 사용하는 안정적인 오류 식별자 |
| `message` | 개발 및 기본 안내용 메시지 |
| `path` | 실패한 요청 경로 |
| `fieldErrors` | 필드 검증 실패 정보. 해당 사항이 없으면 빈 객체 `{}` |

`code`, `status`, 필드 구조는 API 계약입니다. `message` 문구는 개선될 수 있으므로 로직 분기 기준으로 사용하지 않습니다.

## 공통 오류 코드

| HTTP | code | 발생 상황 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 요청 DTO의 필드 검증 실패 |
| 400 | `INVALID_REQUEST_BODY` | JSON 문법 오류 또는 읽을 수 없는 요청 본문 |
| 400 | `INVALID_PARAMETER` | 경로·쿼리 파라미터 타입 변환 실패 |
| 400 | `MISSING_PARAMETER` | 필수 쿼리 파라미터 누락 |
| 401 | `AUTHENTICATION_REQUIRED` | Access Token을 보내지 않음 |
| 401 | `INVALID_ACCESS_TOKEN` | Access Token이 만료·위조되었거나 유효하지 않음 |
| 403 | `ACCESS_DENIED` | 인증은 됐지만 요청 권한이 없음 |
| 404 | `API_NOT_FOUND` | 존재하지 않는 API 경로 요청 |
| 405 | `METHOD_NOT_ALLOWED` | 해당 경로에서 지원하지 않는 HTTP 메서드 사용 |
| 409 | `DATA_INTEGRITY_CONFLICT` | DB 고유 제약 등 저장 데이터 충돌 |
| 500 | `INTERNAL_SERVER_ERROR` | 공개하면 안 되는 내부 예외 발생 |

서버 내부 예외의 상세 메시지와 스택 트레이스는 응답에 포함하지 않고 서버 로그에만 남깁니다.

## 도메인 오류 코드

업무 규칙 오류도 같은 응답 구조를 사용합니다. 대표 코드는 다음과 같습니다.

| 영역 | 대표 code |
| --- | --- |
| 인증 | `EMAIL_ALREADY_EXISTS`, `INVALID_PASSWORD`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 현장·공정 | `SITE_NOT_FOUND`, `ALREADY_SITE_MEMBER`, `SITE_MANAGEMENT_FORBIDDEN`, `PROCESS_KEY_ALREADY_EXISTS`, `INVALID_PROCESS_PLAN_PERIOD` |
| 보고서 | `REPORT_NOT_FOUND`, `REPORT_SUBMIT_FORBIDDEN`, `REPORT_REVIEW_FORBIDDEN`, `PENDING_REPORT_ALREADY_EXISTS`, `REPORT_PROGRESS_MUST_INCREASE`, `REPORT_ALREADY_REVIEWED`, `STALE_REPORT_PROGRESS` |
| 자재 | `MATERIAL_REQUEST_NOT_FOUND`, `MATERIAL_REQUEST_SUBMIT_FORBIDDEN`, `MATERIAL_REQUEST_MANAGEMENT_FORBIDDEN`, `INVALID_MATERIAL_STATUS_TRANSITION`, `MATERIAL_REJECT_REASON_REQUIRED` |
| 알림 | `NOTIFICATION_NOT_FOUND` |

## 프론트엔드 처리 규칙

1. HTTP 상태로 인증·권한·입력·서버 오류의 큰 범주를 나눕니다.
2. 세부 화면 동작과 사용자 문구는 `code` 기준으로 매핑합니다.
3. `fieldErrors`가 비어 있지 않으면 각 입력 필드 옆에 표시합니다.
4. `AUTHENTICATION_REQUIRED`와 `INVALID_ACCESS_TOKEN`은 로그인 또는 토큰 갱신 흐름으로 처리합니다.
5. `ACCESS_DENIED`는 토큰 갱신으로 해결되지 않으므로 권한 안내를 표시합니다.
6. `INTERNAL_SERVER_ERROR`는 상세 원인을 추측하지 말고 일반 안내와 재시도 수단을 제공합니다.

새 오류 코드를 추가하거나 기존 코드의 의미·상태를 바꿀 때는 이 문서와 관련 테스트를 함께 수정합니다.
