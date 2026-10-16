# DB·보안 기반 백엔드/API 인계 사항

현재 DB·보안 설계를 백엔드/API 코드에서 깨뜨리지 않기 위해 담당자가 알아야 할 규칙과 남은 구현만 기록합니다.
소스 기준 스키마는 Flyway V1~V15이며 실제 DB 적용 버전은 `flyway_schema_history`에서 확인합니다.

## 1. 공통 구현 원칙

- 이미 적용된 Flyway 파일은 수정하지 않고 다음 버전 마이그레이션을 추가합니다.
- Hibernate `ddl-auto=validate`를 유지하고 운영 스키마를 JPA가 자동 변경하게 만들지 않습니다.
- DTO뿐 아니라 서비스·엔티티에서도 업무 규칙을 검증하고, 최소 불변조건은 DB `CHECK`, `UNIQUE`, `FK`로 보강합니다.
- 상태와 이력을 함께 변경하는 작업은 하나의 `@Transactional` 범위에서 처리합니다.
- 다른 사용자의 리소스 존재를 숨겨야 하는 조회는 권한이 없을 때도 `404`로 처리합니다.
- S3 객체는 DB 커밋 성공 후 애플리케이션에서 삭제하며 DB 연쇄 삭제만 믿지 않습니다.
- V10 적용 전에는 레거시 삭제 대상이 있는지 `deploy/preflight-v10.sql`로 확인합니다.

## 2. 백엔드/API가 지켜야 할 DB·보안 규칙

### 사용자·회사·인증

- 사용자 기본 정보는 `users`, 회사 소속과 본사 권한은 `company_memberships`를 기준으로 합니다.
- 제거된 `users.company`, `hq_company_id`, `manager_company_id`, `legacy_auth_tokens`에 의존하는 코드를 만들지 않습니다.
- 현장 역할과 회사 역할은 별개이며 본사 관리자는 `HQ_MANAGER` 멤버십으로 판정합니다.
- 비밀번호는 BCrypt 해시만 저장합니다.
- Access Token은 단기 JWT이고 Refresh Token 원문은 DB에 저장하지 않습니다.
- Refresh Token은 SHA-256 해시, 만료 시각, 폐기 시각으로 검증하며 만료·로그아웃 토큰의 재사용을 거부합니다.

### 현장·공정·권한

- `sites.owner_id`와 활성 `site_members.OWNER`는 같은 사용자여야 하며 OWNER는 한 명이어야 합니다.
- `OWNER`·`MANAGER`는 관리/검토, `WORKER`는 작성, `VIEWER`는 조회만 허용하는 현재 정책을 유지합니다.
- `SYSTEM_ADMIN`은 전역 권한이고 `HQ_MANAGER`는 소속 회사 현장에 대한 본사 관리 권한입니다.
- 현장 위도·경도는 함께 입력하고 위도 `-90~90`, 경도 `-180~180`을 검증합니다.
- `process_key`는 현장 안의 고정 식별자이고 `process_name`은 보고서·자재 요청 작성 당시 표시 이름 스냅샷입니다.
- 공정 가중치는 `0 초과~100 이하`, 진행률은 `0~100`, 정렬 순서는 `0 이상`입니다.

### 보고서·S3

- 보고서 생성 시 현장의 실제 `process_key`를 확인하고 당시 공정명과 시작 진행률을 저장합니다.
- 목표 진행률은 시작 진행률보다 크고 `100` 이하여야 하며 동일 공정의 `PENDING` 보고서를 중복 생성하지 않습니다.
- 승인 시 보고서와 공정을 잠근 뒤 시작 진행률을 다시 검증하고 공정 진행률과 승인 상태를 함께 변경합니다.
- 제출/사진 위치는 좌표 쌍과 범위를 검증하고 위치 정확도·파일 크기는 음수를 허용하지 않습니다.
- 사진과 첨부파일 DB에는 S3 객체가 아니라 `s3_key`, 원본명, MIME, 크기 등 메타데이터를 저장합니다.
- `report_files.path`는 기존 데이터 호환용이며 신규 업로드는 `s3_key`를 사용합니다. 둘 중 하나는 반드시 존재해야 합니다.
- `report_checks.photo_id`는 특정 사진 검사 결과를 연결하며 사진 삭제 시 해당 검사만 연쇄 삭제합니다.
- 사진·첨부파일 변경과 보고서 삭제는 `PENDING` 상태에서 작성자·관리자·시스템 관리자만 허용합니다.
- 업로드 후 DB 롤백 시 새 S3 객체를 정리하고, 삭제는 DB 커밋 후 S3 객체를 정리합니다.

### 자재 요청

- 실제 현장 공정을 확인하고 `process_key`와 당시 `process_name`을 저장합니다.
- 품목은 한 개 이상이고 수량은 양수여야 합니다.
- 상태는 `REQUESTED → APPROVED/REJECTED`, `APPROVED → ORDERED → DELIVERED`만 허용합니다.
- `REJECTED`에만 반려 사유가 필요하고 다른 상태에는 반려 사유를 저장하지 않습니다.
- 현재 상태와 마지막 이벤트 상태는 항상 같아야 하며 처리자는 `actor_user_id`로 남깁니다.
- 동시 상태 변경은 비관적 잠금으로 직렬화하고 최신 상태를 다시 검사합니다.
- 현장 구성원만 작성/조회하며 OWNER·MANAGER·HQ_MANAGER·SYSTEM_ADMIN만 상태를 변경합니다.

### 알림·기기 토큰

- 알림 목록·미읽음 수·읽음 처리는 현재 사용자 ID로 제한합니다.
- `reference_type`과 `reference_id`는 함께 존재하거나 함께 없어야 하며 `read_at`은 생성 시각보다 빠를 수 없습니다.
- 보고서 승인/반려와 자재 요청 상태 변경 시 작성자에게 알림을 생성합니다.
- 기기 토큰은 공백일 수 없고 전체 시스템에서 유일합니다.
- 재등록하면 `updated_at`을 갱신하고 다른 사용자가 같은 토큰을 등록하면 최신 사용자에게 소유권을 이전합니다.
- 토큰 삭제는 현재 소유자에게만 영향을 줍니다.

## 3. 구현 상태와 남은 작업

### 구현 완료

- [x] 모바일 현장·보고서 필드 JPA/DTO/서비스/API 연결
- [x] 보고서 사진·일반 첨부파일 S3 업로드·조회·다운로드·삭제 및 권한 검증
- [x] 보고서 위치·사진 검사 생성과 응답 연결
- [x] 현장 OWNER 일관성 검증
- [x] 자재 요청 품목·공정·상태 전환·반려 사유·권한·잠금·이벤트 일관성 검증
- [x] 알림 사용자 격리·읽음 처리·업무 이벤트 알림 검증
- [x] 기기 토큰 재등록 시각·소유권 이전·본인 삭제·공백 차단 검증

### 아직 구현할 작업

#### [ ] 보고서 내용 수정 API

- `PENDING` 보고서의 작업 내용·메모·날씨·작업자 수·장비 등 확정된 편집 필드만 수정합니다.
- 작성자와 허용된 관리자만 수정하고 현장·작성자·공정 식별/스냅샷·진행률·검토 상태는 변경하지 않습니다.
- 첨부파일은 기존 사진/파일 API로 변경하며 수정 DTO에서 임의의 S3 키를 받지 않습니다.
- 권한·상태·입력 검증과 공통 오류 응답 자동 테스트를 함께 추가합니다.

### 정책 확정 후 구현

- 알림 종류/참조 종류 허용 목록과 보관·삭제 기간
- 여러 참조 대상의 존재 검증과 대상 삭제 후 알림 처리
- 기기 플랫폼, 활성/폐기 상태, 로그아웃 연동, 오래된 토큰 정리 기간
- FCM/APNs 실패 토큰 정리와 동시 최초 등록 충돌 처리 방식

## 4. 백엔드/API 담당자에게 넘길 최소 핵심 파일

전체 프로젝트를 공유할 수 있다면 일부 파일만 복사하기보다 저장소 브랜치로 전달하는 것이 안전합니다. 별도 설명용으로 최소 파일만 추린다면 다음 구조를 사용합니다.

```text
site/
  service/SiteAccessService.java          # 모든 현장·본사 권한 판정의 중심
  service/SiteManagementService.java      # 현장 위치와 OWNER 일관성

report/
  service/ReportService.java              # 작성·조회·삭제 및 진행률/검사 연결
  service/ReportReviewService.java        # 승인·반려, 잠금, 진행률, 알림
  service/ReportPhotoService.java         # 사진 권한과 DB/S3 트랜잭션 경계
  service/ReportFileService.java          # 일반 첨부파일 권한과 DB/S3 경계
  service/ReportCheckService.java         # 위치·촬영 검사 규칙
  dto/CreateReportRequest.java            # 모바일 필드를 포함한 생성 계약

material/
  entity/MaterialRequestStatus.java       # 허용 상태 전환표
  repository/MaterialRequestRepository.java # 상태 변경용 비관적 잠금
  service/MaterialRequestService.java     # 품목·공정·권한·상태·이벤트 규칙

notification/
  service/NotificationService.java        # 사용자 격리와 읽음 처리

device/
  service/DeviceTokenService.java         # 등록·소유권 이전·삭제 생명주기

common/
  exception/GlobalExceptionHandler.java   # 공통 API 오류 규격
```

관련 DB 마이그레이션과 엔티티 없이 위 서비스만 전달하면 필드와 제약을 해석할 수 없으므로 V10~V15 및 아래 핵심 엔티티도 함께 공유해야 합니다.

```text
User.java, Site.java, Report.java, ReportPhoto.java, ReportFile.java,
MaterialRequest.java, MaterialRequestEvent.java, Notification.java, DeviceToken.java
```

## 5. 점검 시작 전과 현재 마이그레이션 비교

### 시작 전: V1~V9

- V1: 최초 사용자·회사·현장·공정·보고서·자재·기기 토큰 구조
- V2: 원문 토큰 구조를 레거시로 바꾸고 해시 기반 Refresh Token 테이블 추가
- V3: 현장 역할과 회사 멤버십 추가
- V4~V5: 보고서 승인 이력과 자재 처리 사용자 연결
- V6~V8: 컬렉션 위치 유일성, 조회 인덱스, 알림, 현장 OWNER 멤버십 보정
- V9: 모바일 위치/작업 필드와 S3 메타데이터, `report_checks` 구조 준비

### 현재 추가: V10~V15

- V10: 레거시 인증 테이블과 사용자 회사 컬럼 제거, 회사 멤버십 이전 검증
- V10: 사진의 `file_name` 제거와 원본명 통합, 일반 첨부파일의 레거시 path/S3 병행 구조 완성
- V10: 현장·보고서·사진 위치 쌍/범위, 위치 정확도, 파일 크기 제약 추가
- V10: 검사 결과에 `photo_id`를 연결하고 사진 삭제 시 해당 검사 결과만 연쇄 삭제
- V11: 공정 가중치·진행률·정렬값, 보고서 진행률·작업자 수, 자재 수량 범위 제약 추가
- V12: 자재 상태와 품목·이벤트 `position` 제약, 처리 사용자 필수화, 기존 데이터 일관성 검사 추가
- V13: 알림 참조값 쌍을 포함한 알림·기기 토큰 기본 제약 추가
- V14: 모바일 보고서 `work` 필수화와 선택형 공정 연결 컬럼의 JPA 매핑 정리
- V15: 캡스톤 범위에 맞춰 변경 가능한 업무 규칙과 낙관적 잠금용 컬럼을 제거
- 운영 데이터 삭제가 포함된 V10에 대해서만 별도 preflight SQL 유지

## 6. 이번 점검에서 추가된 보안·무결성 보완

- 레거시 인증 토큰 테이블을 제거해 사용하지 않는 인증 데이터 경로를 없앴습니다.
- 회사 권한의 기준을 `company_memberships`로 단일화하고 이전 누락 시 마이그레이션을 중단합니다.
- 현장 소유자와 OWNER 멤버십 불일치를 서비스에서 차단합니다.
- 보고서·사진·첨부파일 접근은 현장 접근권한, 작성자/관리자, `PENDING` 상태를 함께 검사합니다.
- 자재 요청 상태 변경은 역할 검증, 허용 전환, 마지막 이벤트 일치, 비관적 잠금을 함께 사용합니다.
- 알림 조회·읽음은 사용자 소유 범위로 제한하고 다른 사용자 알림은 `404`로 처리합니다.
- 기기 토큰은 전역 유일성과 소유자 삭제 범위를 유지하고 공백 입력은 API에서 차단합니다.
- 중요 값은 애플리케이션 검증만 믿지 않고 DB 제약으로 우회 입력도 차단합니다.
- 이번 점검에서는 JWT 서명 방식·Access Token 수명·BCrypt 방식 자체는 변경하지 않았습니다.

## 7. DB·JPA 핵심 파일 지도

```text
src/main/resources/db/migration/
  V1__...sql ~ V15__...sql                # 최종 스키마 변경 이력
deploy/
  preflight-v10.sql                        # 레거시 데이터 삭제 전 검사

entity/
  user/User.java
  company/Company.java, CompanyMembership.java
  site/Site.java, SiteMember.java, SiteProcess.java
  report/Report.java, ReportPhoto.java, ReportFile.java,
         ReportCheck.java, ReportReviewEvent.java
  material/MaterialRequest.java, MaterialRequestItem.java,
           MaterialRequestEvent.java, MaterialRequestStatus.java
  auth/RefreshToken.java
  notification/Notification.java
  device/DeviceToken.java

repository/
  CompanyMembershipRepository.java        # 본사 역할 조회
  SiteMemberRepository.java               # 현장 역할·OWNER 검증
  SiteProcessRepository.java              # 공정 식별과 진행률 조회
  ReportRepository.java                   # 보고서 조회와 검토·파일 변경 잠금
  MaterialRequestRepository.java          # 자재 상태 변경 잠금
  RefreshTokenRepository.java             # 해시 토큰 조회
  NotificationRepository.java             # 사용자별 알림 조회/읽음
  DeviceTokenRepository.java              # 전역 토큰 조회와 소유자 삭제

configuration/
  application.yml                         # DB/Flyway/ddl-auto 및 환경변수 계약
  application-test.yml                    # H2 테스트 매핑; Flyway 제약 검증과는 별개
```

## 8. 보안 핵심 파일 지도

```text
common/config/
  SecurityConfig.java                     # 공개 경로, JWT 인증, 무상태 세션, CORS
  PasswordConfig.java                     # BCrypt PasswordEncoder
  CorsProperties.java                     # 허용 Origin 설정

auth/
  jwt/JwtProperties.java                  # Secret·Issuer·만료시간 설정
  jwt/JwtConfig.java                      # JWT Encoder/Decoder
  jwt/JwtTokenService.java                # Access Token 발급
  service/PasswordService.java            # BCrypt 해시·검증
  service/RefreshTokenService.java        # 랜덤 토큰, SHA-256, 만료·폐기
  service/AuthService.java                # 가입·로그인·재발급·로그아웃 흐름
  entity/RefreshToken.java
  repository/RefreshTokenRepository.java

authorization/
  site/service/SiteAccessService.java     # 도메인 역할 권한의 단일 진입점
  user/service/CurrentUserService.java    # JWT subject에서 현재 사용자 확인

error/
  GlobalExceptionHandler.java             # 공통 오류 응답과 서버 오류 은닉
  RestAuthenticationEntryPoint.java       # 미인증 401 JSON 응답
  RestAccessDeniedHandler.java             # 권한 부족 403 JSON 응답

tests/
  AuthServiceTest.java
  RefreshTokenServiceTest.java
  SecurityErrorHandlerTest.java
  CoreApiIntegrationTest.java
  MaterialRequestIntegrationTest.java
  NotificationIntegrationTest.java
  DeviceTokenIntegrationTest.java
```

## 9. 병합·배포 전 확인 순서

1. V10 적용 전이라면 `preflight-v10.sql`로 레거시 삭제 대상을 확인합니다.
2. 대상 DB의 `flyway_schema_history` 현재 버전을 확인합니다.
3. Windows에서는 `.\gradlew.bat test`로 전체 테스트를 실행합니다.
4. 시작 로그에서 Flyway 적용과 Hibernate `validate` 성공을 확인합니다.
5. 인증, 타 사용자/현장 접근 차단, 보고서 승인, 자재 상태 변경, 알림, 기기 토큰을 통합 검증합니다.
6. 운영 반영 전 DB 백업과 이전 JAR을 보관하고 반영 후 로그와 `/api/health`를 확인합니다.
