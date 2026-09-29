# BuildUp 백엔드 로컬 실행 가이드

이 문서는 저장소를 처음 clone한 팀원이 Windows PowerShell에서 백엔드를 실행하고 상태를 확인하는 절차만 설명합니다. API 사용법은 `docs/API.md`, Git 협업 규칙은 `docs/COLLABORATION.md`를 참고하세요.

## 1. 준비물

- Git
- Java 17 이상: `java -version`으로 확인
- MySQL 8.x: 로컬 서비스가 실행 중이어야 함

Gradle은 별도로 설치하지 않습니다. 저장소에 포함된 Gradle Wrapper(`gradlew.bat`)를 사용합니다.

## 2. clone 및 이동

```powershell
git clone https://github.com/hiallu60/buildup_backend.git
cd buildup_backend
```

## 3. 로컬 데이터베이스 준비

MySQL 관리자 계정으로 접속합니다.

```powershell
mysql -u root -p
```

아래 SQL의 비밀번호는 각자 정한 로컬 개발용 값으로 바꾸세요. 실제 비밀번호를 Git에 커밋하지 않습니다.

```sql
CREATE DATABASE IF NOT EXISTS buildup_local
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'buildup_app'@'localhost'
  IDENTIFIED BY '로컬에서_정한_비밀번호';

GRANT ALL PRIVILEGES ON buildup_local.* TO 'buildup_app'@'localhost';
FLUSH PRIVILEGES;
```

이미 같은 사용자가 있는데 비밀번호가 다르다면 새로 만들지 말고 본인의 MySQL 설정에 맞춰 `BUILDUP_DB_USERNAME`과 `BUILDUP_DB_PASSWORD`를 지정합니다.

## 4. 환경변수 설정

PowerShell 창에 아래 값을 설정합니다. 이 값들은 현재 터미널 세션에만 유지됩니다.

```powershell
$env:BUILDUP_DB_URL = "jdbc:mysql://localhost:3306/buildup_local?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul"
$env:BUILDUP_DB_USERNAME = "buildup_app"
$env:BUILDUP_DB_PASSWORD = "본인의_로컬_DB_비밀번호"
$env:BUILDUP_JWT_SECRET = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
$env:BUILDUP_CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost:3000"
```

- `BUILDUP_JWT_SECRET`은 Base64 형식의 256비트 이상 값이어야 합니다.
- 위 명령은 개발용 Secret을 매번 새로 만듭니다. 서버 재시작 후 기존 Access Token이 무효가 되어도 괜찮은 로컬 개발에 적합합니다.
- 팀 공용·운영 Secret은 채팅, 문서, Git에 기록하지 않고 배포 환경의 Secret으로 관리합니다.
- 프론트엔드 개발 서버의 Origin이 다르면 `BUILDUP_CORS_ALLOWED_ORIGINS`에 쉼표로 추가합니다. 끝의 `/`는 붙이지 않습니다.

선택 환경변수:

| 이름 | 기본값 | 용도 |
| --- | --- | --- |
| `BUILDUP_SERVER_PORT` | `8080` | 백엔드 포트 |
| `BUILDUP_DB_URL` | 로컬 `buildup_local` URL | DB 연결 주소 |
| `BUILDUP_DB_USERNAME` | `buildup_app` | DB 사용자 |
| `BUILDUP_CORS_ALLOWED_ORIGINS` | `localhost:5173, localhost:3000` | 허용할 프론트엔드 Origin |

`BUILDUP_DB_PASSWORD`와 `BUILDUP_JWT_SECRET`에는 기본값이 없으므로 반드시 설정해야 합니다.

## 5. 실행

```powershell
.\gradlew.bat bootRun
```

첫 실행에는 의존성 다운로드로 시간이 걸릴 수 있습니다. 애플리케이션 시작 과정에서 Flyway가 아직 적용되지 않은 migration을 순서대로 실행하고, Hibernate는 스키마가 엔티티와 맞는지 `validate`합니다.

다음 로그를 확인한 뒤 새 PowerShell 창에서 상태를 점검합니다.

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

예상 결과:

```text
status message
------ -------
ok     BuildUp API is running
```

서버 종료는 실행 중인 창에서 `Ctrl+C`입니다.

## 6. 기본 검증

변경 전후에 테스트를 실행합니다.

```powershell
.\gradlew.bat test
```

로그인까지 확인하려면 `docs/API.md`의 회원가입 또는 로그인 요청을 보냅니다. 실제 이메일, 비밀번호, Access Token, Refresh Token은 캡처나 커밋에 포함하지 않습니다.

## 7. 데이터베이스 변경 규칙

- 기존 `src/main/resources/db/migration/Vn__*.sql`은 수정하지 않습니다.
- 변경이 필요하면 현재 마지막 버전 다음 번호의 migration을 새로 추가합니다.
- 테이블을 DBeaver 화면에서 직접 수정해 팀 스키마로 삼지 않습니다.
- `spring.jpa.hibernate.ddl-auto=validate`를 유지합니다. JPA가 운영 스키마를 임의 생성·수정하게 하지 않습니다.
- Flyway 오류가 나면 migration을 삭제하거나 기록을 직접 고치기 전에 팀에 공유합니다.

## 8. 자주 생기는 문제

### `BUILDUP_DB_PASSWORD` 또는 `BUILDUP_JWT_SECRET` 관련 오류

환경변수를 설정한 PowerShell 창과 서버를 실행한 창이 같은지 확인합니다. 새 터미널을 열었다면 다시 설정해야 합니다.

### `Access denied for user 'buildup_app'`

사용자명·비밀번호와 MySQL 권한을 확인합니다. 애플리케이션 설정을 바꾸기 전에 터미널에서 같은 계정으로 접속 가능한지 확인하세요.

```powershell
mysql -u buildup_app -p buildup_local
```

### 포트 8080이 이미 사용 중

기존 서버를 종료하거나 다른 포트를 지정합니다.

```powershell
$env:BUILDUP_SERVER_PORT = "8081"
.\gradlew.bat bootRun
```

이 경우 Base URL도 `http://localhost:8081`로 바뀝니다.

### 브라우저에서 CORS 오류

프론트엔드 주소의 스킴·호스트·포트를 정확히 `BUILDUP_CORS_ALLOWED_ORIGINS`에 넣고 백엔드를 재시작합니다. 예: `http://localhost:5173`. DB 설정이나 JWT Secret을 프론트엔드에 넣는 방식으로 해결하지 않습니다.
