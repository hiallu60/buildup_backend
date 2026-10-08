# CHANGELOG

프로젝트의 주요 변경사항을 기록합니다.

## [Unreleased]

### Added
- 알림 API 검증
- 디바이스 토큰 API 검증
- Refresh Token 서버 측 해시 저장·만료 검증·로그아웃 무효화 테스트
- 로그인 성공·실패, 인증·권한 거부, 보고서 생성·승인·반려 로그
- DB 오류와 처리되지 않은 서버 예외의 구조화된 오류 로그
- 개발환경 콘솔 전용 로그와 운영환경 파일 전용 로그 분리
- 운영 로그 일별 Rotation, gzip 압축 및 14일 보관 설정
- 로그인·JWT 인증·Refresh Token 재발급·로그아웃 무효화 통합 테스트
- 현장 참여·중복 참여 방지·권한 검사·보고서 작성·승인 통합 테스트
- AWS SDK for Java 2.x 기반 S3 클라이언트와 환경변수 설정
- IAM Role 기반 S3 버킷 연결 확인 서비스와 조건부 확인 API
- S3 버킷 연결 요청 단위 테스트

### Changed
- Access Token 재발급 시 기존 Refresh Token을 유지하도록 Rotation 제거
- 서버 로그를 `event`와 식별자 중심의 INFO·WARN·ERROR 기준으로 정리

### Fixed
- 만료되거나 로그아웃으로 폐기된 Refresh Token의 재사용 차단을 명시적으로 검증

### Security
- 비밀번호, Access Token, Refresh Token, Authorization 헤더 및 요청 본문을 로그에서 제외
- AWS Access Key를 애플리케이션 설정에 저장하지 않고 EC2 IAM Role 기본 자격 증명 체인을 사용

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
