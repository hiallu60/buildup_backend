# BuildUp Git 협업 가이드

이 문서는 팀원이 `main`을 안전하게 공유하면서 기능 브랜치에서 작업하고 Pull Request를 만드는 절차만 정리합니다. 프로젝트 소개는 `README.md`, 코드·DB 원칙은 `AGENTS.md`, 변경 이력은 `CHANGELOG.md`를 참고하세요.

## 1. 기본 원칙

- `main`은 실행·검증 가능한 기준 브랜치로 유지합니다.
- 기능, 수정, 문서 작업은 각각 별도 브랜치에서 진행합니다.
- `main`에 직접 push하지 않고 Pull Request로 합칩니다.
- 한 브랜치에는 한 가지 목적의 변경만 담습니다.
- 비밀번호, JWT Secret, 토큰, 운영 주소의 비공개 값, 개인 `.env`는 커밋하지 않습니다.

## 2. 작업 시작

현재 변경사항을 먼저 확인합니다.

```powershell
git status
```

작업 중인 변경이 없다면 `main`을 최신화하고 브랜치를 만듭니다.

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/report-api
```

브랜치 이름은 소문자 영문과 하이픈을 사용합니다.

| 종류 | 예시 | 용도 |
| --- | --- | --- |
| `feature/` | `feature/site-member-api` | 새 기능 |
| `fix/` | `fix/refresh-token-error` | 버그 수정 |
| `refactor/` | `refactor/report-service` | 동작을 바꾸지 않는 구조 개선 |
| `docs/` | `docs/local-setup` | 문서만 변경 |
| `chore/` | `chore/test-config` | 설정·도구·잡무 |

## 3. 구현 중 지킬 것

### API 변경

- Controller의 경로·HTTP 메서드·요청 DTO·응답 DTO를 함께 확인합니다.
- 프론트엔드 계약이 바뀌면 같은 Pull Request에서 `docs/API.md`도 갱신합니다.
- 기존 클라이언트가 깨지는 변경은 PR 설명에 명확히 표시하고 팀과 먼저 합의합니다.
- 보호 API는 현장 멤버십과 역할 검사를 우회하지 않도록 Service까지 확인합니다.

### DB 변경

- 이미 적용된 Flyway migration은 수정하지 않습니다.
- 새 `Vn__설명.sql`을 추가하고, Entity 변경과 함께 검증합니다.
- 데이터 삭제·컬럼 제거처럼 되돌리기 어려운 변경은 구현 전에 팀과 합의합니다.
- `ddl-auto=validate`를 유지합니다.

### 보안

- 비밀번호는 BCrypt 흐름을 유지합니다.
- Access JWT + 해시된 Refresh Token + Rotation 구조를 우회하지 않습니다.
- 클라이언트 입력으로 전역 역할이나 현장 권한을 임의 부여하지 않습니다.
- 오류 메시지나 로그에 비밀번호와 토큰을 출력하지 않습니다.

## 4. 커밋 전 확인

```powershell
.\gradlew.bat test
git status
git diff
```

다음을 확인합니다.

- 변경 목적과 관계없는 파일이 포함되지 않았는가
- `.env`, Secret, 토큰, 개인 IDE 설정이 포함되지 않았는가
- 새 API나 변경된 API가 `docs/API.md`에 반영되었는가
- 의미 있는 기능 단위가 완성되었다면 `CHANGELOG.md`의 `[Unreleased]`에 기록했는가
- DB 변경이라면 새 migration이며 기존 migration을 수정하지 않았는가

## 5. 커밋과 push

커밋 메시지는 `type: 요약` 형식을 권장합니다.

```powershell
git add src docs
git commit -m "feat: add site member role api"
git push -u origin feature/site-member-api
```

자주 쓰는 type:

- `feat`: 기능 추가
- `fix`: 버그 수정
- `refactor`: 기능 변화 없는 구조 개선
- `test`: 테스트 추가·수정
- `docs`: 문서 변경
- `chore`: 설정·빌드 작업

`git add .`을 사용해도 되지만, 커밋 전 `git diff --cached`로 포함 파일을 반드시 확인합니다.

## 6. Pull Request

PR 제목 예시: `feat: 현장 멤버 역할 변경 API 추가`

PR 본문에는 다음을 적습니다.

```markdown
## 변경 내용
- 무엇을 추가·수정했는지

## 확인 방법
- 실행한 테스트
- 호출한 API와 예상 결과

## 영향 범위
- 프론트엔드 계약 변경 여부
- DB migration 여부
- 필요한 환경변수 변경 여부

## 체크리스트
- [ ] 테스트 통과
- [ ] Secret 미포함
- [ ] API 문서 갱신
- [ ] 필요한 경우 CHANGELOG 갱신
```

리뷰어는 최소한 API 계약, 권한 검사, migration 안전성, 테스트 결과를 확인합니다. 리뷰 수정은 같은 브랜치에 추가 커밋으로 push합니다.

## 7. 작업 중 `main` 변경 반영

먼저 현재 작업을 커밋한 뒤 최신 `main`을 가져옵니다.

```powershell
git fetch origin
git rebase origin/main
```

충돌이 나면 각 파일의 의도를 확인해 해결하고 테스트합니다.

```powershell
git add 충돌을_해결한_파일
git rebase --continue
.\gradlew.bat test
git push --force-with-lease
```

- 공유 브랜치라면 rebase 전에 함께 쓰는 팀원에게 알립니다.
- 강제 push가 필요할 때도 `--force` 대신 `--force-with-lease`를 사용합니다.
- 충돌 해결이 불확실하면 임의로 한쪽을 버리지 말고 작성자에게 확인합니다.

## 8. 병합 후 정리

PR이 병합되면 로컬 기준 브랜치를 갱신합니다.

```powershell
git switch main
git pull --ff-only origin main
git branch -d feature/site-member-api
```

원격 브랜치는 GitHub에서 삭제하거나 다음 명령을 사용합니다.

```powershell
git push origin --delete feature/site-member-api
```

EC2 배포는 `main` 병합과 별도 단계입니다. PR이 병합되었다고 배포가 완료된 것은 아니며, 배포 담당자가 서버 상태·migration·헬스 체크를 확인한 뒤 결과를 공유합니다.
