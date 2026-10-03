# CHANGELOG

프로젝트의 주요 변경사항을 기록합니다.

## [Unreleased]

### Added
- 알림 API 검증
- 디바이스 토큰 API 검증

### Changed
- 없음

### Fixed
- 없음

---

## [0.1.1] - 2026-10-03

### Added
- 요청 검증, 잘못된 JSON·파라미터, 미지원 메서드, 미존재 API, DB 충돌, 서버 내부 오류의 공통 응답 처리
- Access Token 누락·오류와 권한 부족에 대한 JSON 오류 응답
- 공통 오류 코드 및 프론트엔드 처리 규격 문서
- 오류 처리 자동 테스트 11건

### Changed
- 모든 API 오류 응답을 `timestamp`, `status`, `code`, `message`, `path`, `fieldErrors` 구조로 통일

### Security
- 예상하지 못한 서버 예외의 상세 내용을 응답에서 숨기고 서버 로그에만 기록

---

## [0.1.0] - 2026-09-30

### Added
- Spring Security + JWT 인증
- Refresh Token Rotation 및 로그아웃
- 현장 및 현장 멤버 관리
- 공정 생성·조회·수정
- 보고서 생성·조회·승인·반려
- 승인 보고서 기반 공정 진행률 갱신
- 자재 요청 및 상태 관리
- 알림 및 디바이스 토큰 구조
- Flyway V1~V8
- AWS EC2/RDS 운영환경

### Security
- BCrypt 비밀번호 저장
- 역할 기반 API 접근 제어
- DB/JWT Secret 환경변수 분리
