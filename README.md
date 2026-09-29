# BuildUp Backend

건설 현장 공정, 보고서, 자재 요청을 관리하는 BuildUp 프로젝트의 백엔드 서버입니다.

## Tech Stack

- Java 17
- Spring Boot 4.1.1
- Spring Security
- JWT
- Spring Data JPA
- MySQL
- Flyway
- Gradle

## Database

데이터베이스 변경은 Flyway migration으로 관리합니다.

이미 적용된 migration 파일은 수정하지 않고,
새로운 `Vn__*.sql` 파일을 추가합니다.

## Authentication

- BCrypt 비밀번호 저장
- Access JWT
- Refresh Token Rotation
- 역할 기반 접근 제어

## Run

환경변수를 설정한 후 실행합니다.

```bash
./gradlew bootRun