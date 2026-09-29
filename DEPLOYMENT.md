# BuildUp EC2 배포 안내

## 배포 전 필요한 사용자 입력

다음 값은 AWS 계정과 실제 운영 환경에 따라 달라지므로 직접 결정하거나 입력해야 한다.

- EC2 공인 IP 또는 도메인
- EC2 접속용 SSH 키 경로
- RDS 엔드포인트와 데이터베이스 이름
- RDS 애플리케이션 계정 비밀번호
- 운영용 JWT 비밀키
- 허용할 프론트엔드 주소

비밀번호와 JWT 비밀키는 Git, 채팅, JAR 파일에 넣지 않는다.

## 1. 로컬에서 JAR 생성

PowerShell에서 프로젝트 루트로 이동해 실행한다.

JAR 생성에는 DB 비밀번호나 JWT 비밀키가 필요하지 않다. 이 값들은 EC2에서 애플리케이션을 실행할 때만 `/etc/buildup/buildup.env`로 주입한다.

운영용 JWT 비밀키는 다음 명령으로 생성할 수 있다. 출력값은 비밀 저장소나 EC2 환경변수 파일에만 저장한다.

```powershell
$jwtBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
```

```powershell
.\gradlew.bat clean bootJar
```

생성 파일:

```text
build/libs/buildup-0.0.1-SNAPSHOT.jar
```

## 2. EC2 기본 경로 준비

EC2에는 Java 17 이상이 설치되어 있어야 하며 `/usr/bin/java`에서 실행 가능해야 한다.

```bash
sudo useradd --system --home /opt/buildup --shell /sbin/nologin buildup
sudo mkdir -p /opt/buildup /etc/buildup
sudo chown -R buildup:buildup /opt/buildup
sudo chmod 750 /opt/buildup /etc/buildup
```

## 3. 파일 업로드

로컬 PC에서 실제 키 경로와 EC2 주소로 바꿔 실행한다.

```powershell
scp -i "C:\path\to\key.pem" ".\build\libs\buildup-0.0.1-SNAPSHOT.jar" ec2-user@EC2_PUBLIC_IP:/tmp/buildup.jar
scp -i "C:\path\to\key.pem" ".\deploy\buildup.service" ec2-user@EC2_PUBLIC_IP:/tmp/buildup.service
```

EC2에서 배치한다.

```bash
sudo mv /tmp/buildup.jar /opt/buildup/buildup.jar
sudo mv /tmp/buildup.service /etc/systemd/system/buildup.service
sudo chown buildup:buildup /opt/buildup/buildup.jar
sudo chmod 640 /opt/buildup/buildup.jar
```

## 4. 운영 환경변수 입력

`deploy/buildup.env.example`을 참고해 EC2에서 직접 생성한다.

```bash
sudo vi /etc/buildup/buildup.env
sudo chown root:buildup /etc/buildup/buildup.env
sudo chmod 640 /etc/buildup/buildup.env
```

필수 환경변수:

```text
SPRING_PROFILES_ACTIVE=prod
BUILDUP_DB_URL=jdbc:mysql://RDS_ENDPOINT:3306/buildup?sslMode=REQUIRED&serverTimezone=Asia/Seoul
BUILDUP_DB_USERNAME=buildup_app
BUILDUP_DB_PASSWORD=실제 비밀번호
BUILDUP_JWT_SECRET=Base64 형식의 32바이트 이상 비밀키
BUILDUP_CORS_ALLOWED_ORIGINS=http://프론트엔드주소
```

## 5. 서비스 실행

```bash
sudo systemctl daemon-reload
sudo systemctl enable buildup
sudo systemctl start buildup
sudo systemctl status buildup
```

로그 확인:

```bash
sudo journalctl -u buildup -f
```

Flyway가 V1부터 현재 최신 버전까지 적용되고 Hibernate 검증이 끝나야 서버가 시작된다.

## 6. 임시 HTTP 검증

EC2 보안 그룹의 `8080`은 팀원 공인 IP에서만 접근 가능하게 설정한다.

```bash
curl http://127.0.0.1:8080/api/health
```

팀원 PC에서는 다음 주소를 사용한다.

```text
http://EC2_PUBLIC_IP:8080
```

## 7. RDS 보안 그룹

- RDS는 퍼블릭 액세스를 비활성화한다.
- MySQL `3306` 인바운드 출발지는 EC2 보안 그룹으로 제한한다.
- 프론트엔드나 팀원 PC에 RDS 계정을 전달하지 않는다.

## 8. 도메인과 HTTPS 적용 후

`deploy/nginx/buildup.conf.example`의 도메인을 수정해 Nginx 앞단을 구성한다.
HTTPS 적용이 끝나면 다음 작업을 수행한다.

- 프론트엔드 API 주소를 `https://api.example.com`으로 변경
- `BUILDUP_CORS_ALLOWED_ORIGINS`를 실제 HTTPS 프론트엔드 주소로 변경
- EC2 보안 그룹에서 외부 `8080` 접근 차단
- 외부에는 `80/443`만 공개

## 9. 새 버전 재배포

```bash
sudo systemctl stop buildup
sudo cp /opt/buildup/buildup.jar /opt/buildup/buildup.jar.previous
sudo mv /tmp/buildup.jar /opt/buildup/buildup.jar
sudo chown buildup:buildup /opt/buildup/buildup.jar
sudo systemctl start buildup
sudo systemctl status buildup
```

DB 변경이 있다면 배포 전 RDS 스냅샷을 생성하고 새 Flyway 파일을 검토한다.
