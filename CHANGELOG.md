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