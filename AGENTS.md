# AGENTS.md

## 프로젝트 기본 규칙

- Spring Boot + MySQL + Flyway 사용
- 이미 적용된 Flyway migration은 수정하지 않는다.
- DB 변경은 새로운 `Vn__*.sql` 파일로 추가한다.
- Hibernate `ddl-auto=validate`를 유지한다.
- 비밀번호는 BCrypt로 저장한다.
- 인증은 Spring Security + JWT 구조를 유지한다.

## CHANGELOG 관리

- `CHANGELOG.md`는 작업 지침이 아니라 프로젝트 변경 기록이다.
- 일반 작업 시작 시 반드시 읽을 필요는 없다.
- 의미 있는 기능 단위가 완료되거나 여러 변경이 누적되었을 때 갱신한다.

## HELP 관리

- `HELP.md`는 Spring Boot 및 관련 기술의 공식 문서 참고용 파일이다.
- 일반 작업에서 반드시 읽을 필요는 없다.
- Gradle, JPA, Flyway, Spring Security 등 기술 사용법 확인이 필요할 때만 참고한다.