# 운영 인수인계

학기가 끝난 뒤 이 서비스를 이어 받는 사람이 처음 읽는 문서다. 무엇이 어디서 돌고, 무엇을 정기적으로 봐야 하고, 서비스를 닫을 때 무엇을 지워야 하는지를 한곳에 모았다.
자세한 명령은 이미 있는 문서에 있고, 여기서는 그 문서로 가는 길을 적는다.

| 더 볼 문서 | 내용 |
|---|---|
| [backend/README.md](../backend/README.md) | 로컬 실행, 프로필, 설정 값, 로그와 IP 보관 |
| [deploy/README.md](../deploy/README.md) | 운영 서버 설정, 배포, 되돌리기, 백업, 느린 요청 찾기, 이용 지표 집계 |
| [api-contract.md](./api-contract.md) | API 계약, 9장에 아직 없는 기능 |
| [architecture.md](./architecture.md) | 모듈 경계와 데이터 흐름 |
| [public-data-and-ai.md](./public-data-and-ai.md) | 쓰는 공공데이터와 출처, AI 설명 |

## 1. 무엇이 어디서 도는가

세 저장소가 서울 리전(`ap-northeast-2`)의 Lightsail 서버 세 대에 하나씩 올라간다. 서버끼리는 사설 IP 로 부른다(표는 deploy/README.md 첫머리).

| 저장소 | 서버 | 무엇이 도나 | 배포 |
|---|---|---|---|
| `family-fitness-be` | `ff-backend` (1GB) | Docker Compose 로 app(Spring Boot)과 postgres 18 | **자동.** main 에 `backend/`, `deploy/` 나 워크플로 파일이 바뀌어 올라오면 `backend-deploy` 가 시험, 이미지, 배포를 차례로 돈다 |
| `family-fitness-fe` | `ff-frontend` | Next 서버. 앞에 CloudFront(`https://d249o1o9c1vnhc.cloudfront.net`) | **수동.** 저장소에 배포 워크플로가 없다(`ci.yml` 은 검사만 한다). FE 담당이 따로 올린다 |
| `family-fitness-ai` | `ff-ai` | FastAPI(uvicorn) | **수동.** 띄우는 법은 AI 저장소 README 「운영에서 띄우기」. 워커는 반드시 하나다 |

- 사용자 데이터는 `ff-backend` 의 postgres 에만 있다. AI 서버는 DB 에 쓰지 않고, FE 는 DB 없이 BE 를 부르기만 한다.
- BE 는 공인 IP 에 SSH(22)만 연다. AI 포트도 밖에 열지 않는다(AI 에는 인증이 없어 열면 누구나 LLM 비용을 쓸 수 있다).
- 브랜치는 `develop` 에 PR 을 모으고, 운영에 올릴 때 `develop` 을 `main` 에 병합한다. 세 저장소가 같다.

## 2. 로컬에서 돌리기

```bash
cd backend
./gradlew bootRun     # H2 인메모리, 시드 데이터, 개발용 로그인, AI 스텁. Docker 와 DB 가 없어도 뜬다
./gradlew test        # 전체 시험(H2). PostgreSQL 전용 시험은 Docker 가 있을 때만 돈다
./gradlew spotlessApply   # 커밋 전 포맷 교정. CI 는 spotlessCheck 로 본다
```

JDK 는 아무 버전이나 하나 있으면 되고, 빌드용 JDK 25 는 Gradle 이 처음에 내려받는다. FE 를 붙이는 법은 backend/README.md 「프론트 연동」.

## 3. 배포와 되돌리기

- `develop` 을 `main` 에 병합하면 끝난다. 도중에 app 이 다시 뜨는 1분 남짓은 응답하지 않는다.
- **마이그레이션(Flyway)이 든 배포는 그 전에 Lightsail 스냅숏을 찍는다.** DB 는 되돌아가지 않기 때문이다. 2026-10-29 배포(V173~V175)는 `ff-backend-before-v175-20261029` 를 찍고 올렸다.
- 되돌리기는 서버에서 `bash deploy.sh ghcr.io/family-fitness/family-fitness-be:<앞 커밋 해시>` 다. 이미지만 바뀌고 DB 는 그대로라, 새 열이 생긴 뒤에는 예전 app 이 뜨지 않을 수 있다. 그때는 앞으로 고쳐서 다시 배포한다.
- 배포가 끝나면 서버에서 `curl -s localhost:8080/actuator/health` 가 `UP` 인지, `docker compose logs --since 10m app | grep ERROR` 가 비었는지 본다.

## 4. 정해진 시각에 도는 작업

모두 app 안에서 돈다(서버 cron 이 아니다). 시각은 한국 시각이고, 스레드는 둘이다(`spring.task.scheduling.pool.size`).

| 작업 | 언제 | 하는 일 | 일을 했을 때 남는 로그 |
|---|---|---|---|
| 리프레시 토큰 정리 (`RefreshTokenSweeper`) | 매일 04:00 | 만료된 토큰과 폐기한 지 30일이 지난 토큰 행을 지운다 | `리프레시 토큰 기록 N건을 지웠다` |
| 심사용 계정 정리 (`ReviewAccountSweeper`) | 매일 04:30 | 만든 지 31일이 지난 심사용 계정을 체험 가족째 지운다. 실제 사용자(구글 계정)가 들어온 가족은 남긴다 | `심사용 계정 정리: …개 중 …개 지움` |
| 오늘의 미션 알림 (`NotificationScheduler`) | 매일 07:30 | 그날 미션이 있는 가족에 알림을 만든다 | `MISSION_READY 알림 N건을 만들었다` |
| 다시 재기 알림 (`NotificationScheduler`) | 매일 09:00 | 마지막 측정에서 30일이 지난 구성원이 있는 가족에 알림을 만든다 | `REMEASURE 알림 N건을 만들었다` |
| 리그 정산 (`LeagueSettlementScheduler`) | 매월 1일 00:10 | 지난달 가족 리그를 닫고 다음 달 방을 배정한다 | `… 리그를 닫았다` |
| 멈춘 코치 실행 정리 (`StaleCoachRunSweeper`) | 기동 때, 그 뒤 223초마다 | AI 편성이 223초 넘게 RUNNING 이면(서버가 도중에 죽은 경우) 실패로 바꾸고 잠금을 푼다 | `멈춘 코치 실행 N건을 FAILED 로 정리했다` |

- 알림과 리그 정산은 서버가 그 시각에 꺼져 있었어도 다시 뜰 때 그날 몫을 따라잡는다.
- 시각을 바꾸거나 끄려면 서버 `.env` 에 설정 키를 대문자와 밑줄로 적는다. 예: 심사용 계정 정리를 끄려면 `APP_AUTH_REVIEW_CLEANUP_CRON=-`. 키 이름은 backend/README.md 「프로필」 아래 목록에 있다.
- 심사용 계정 보관 기간(`app.auth.review-cleanup.retention`, 31일)을 30일 이하로 두면 app 이 뜨지 않는다. 리프레시 토큰 수명이 30일이라 아직 쓰는 계정이 지워지지 않게 막은 것이다.
- 심사용 로그인은 2026-10-31 에 닫혔다. 마지막 심사용 계정도 12월 초에는 지워지므로, 그 뒤로 이 작업은 대개 아무것도 하지 않는다.

정해진 작업이 제대로 도는지는 이렇게 본다.

```bash
cd /opt/familyfitness
docker compose logs --since 48h app | grep -E '심사용 계정 정리|리프레시 토큰 기록|알림 .*건을 만들었다|리그를 닫았다|멈춘 코치 실행'
```

## 5. 운영 환경 변수

진짜 값은 서버의 `/opt/familyfitness/.env` 에만 있다. 예시와 설명은 [deploy/.env.example](../deploy/.env.example). 고친 뒤에는 `docker compose up -d` 로 다시 띄운다.

| 이름 | 없으면 | 바꿀 때 조심할 것 |
|---|---|---|
| `BACKEND_IMAGE` | app 이 뜨지 않는다 | 배포 스크립트가 적는다. 손으로 고치지 않는다 |
| `BE_PRIVATE_IP` | app 이 뜨지 않는다 | 서버 설정 스크립트가 적는다 |
| `POSTGRES_PASSWORD` | 뜨지 않는다 | DB 를 처음 만들 때만 쓰인다. 이 줄만 바꾸면 app 이 DB 에 붙지 못한다 |
| `APP_JWT_SECRET` | 뜨지 않는다 | 바꾸면 모든 사람의 로그인이 풀린다 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | 구글 로그인만 안 된다 | FE 의 구글 클라이언트와 같아야 한다 |
| `APP_FRONTEND_BASE_URL` | 뜨지 않는다 | 초대 링크 앞머리다. FE 주소가 바뀌면 함께 바꾼다 |
| `APP_CORS_ALLOWED_ORIGINS` | POST 가 모두 403 이 된다 | FE 주소. 빠져서 로그인이 막힌 적이 있다(2026-10-01) |
| `APP_AI_BASE_URL` | AI 편성이 늘 대체 편성으로 짜인다 | `ff-ai` 의 사설 IP 와 포트 |
| `KMA_SERVICE_KEY` | 편성이 날씨를 보지 않는다. 다른 기능은 그대로다 | 공공데이터포털의 Decoding 키. 맞는지는 편성을 한 번 돌린 뒤 로그에 「기상청 단기예보를 읽지 못했다」가 없는지로 본다 |
| `APP_AUTH_REVIEW_LOGIN_ENABLED`, `APP_AUTH_REVIEW_LOGIN_UNTIL` | 기본값(켬, 2026-10-31 까지) | 마감일이 지나면 켜져 있어도 404 다 |
| `JAVA_TOOL_OPTIONS` | 기본값(`-Xmx256m` 등, compose.yaml) | 서버를 2GB 로 올리면 `-Xmx512m` 까지 올려도 된다 |

배포에 쓰는 비밀값은 저장소 Settings > Secrets 에 있다: `LIGHTSAIL_HOST`, `LIGHTSAIL_SSH_KEY`, `LIGHTSAIL_KNOWN_HOSTS`. AI 서버의 키(`ANTHROPIC_API_KEY` 등)는 AI 서버의 `.env` 에 있고, 이름은 AI 저장소의 `.env.example` 에 있다.

## 6. 외부 계정과 키

| 무엇 | 어디서 관리 | 볼 것 |
|---|---|---|
| AWS (Lightsail 세 대, 고정 IP, 스냅숏, CloudFront) | AWS 계정, CLI 는 `ff-backend` 역할 | 요금. 스냅숏은 저장 용량만큼 요금이 붙고, 서버에 붙어 있지 않은 고정 IP 에도 요금이 붙는다 |
| GHCR 이미지 | GitHub 조직 Packages | 비공개라 되돌리기 때 새로 받으려면 `read:packages` 토큰이 필요하다 |
| 구글 OAuth 클라이언트 | Google Cloud Console | FE 주소가 바뀌면 허용된 리디렉션 주소를 함께 고친다 |
| 기상청 단기예보 인증키 | 공공데이터포털 마이페이지 | 활용기간이 끝나기 전에 연장 신청한다. 2026-10-29 에는 키 등록이 반영되기 전이라 아직 정상 응답(resultCode 00)을 확인하지 못했다 |
| LLM 키(Anthropic, Gemini) | 각 콘솔, AI 서버 `.env` | 사용량과 결제. 키가 없으면 AI 는 규칙 편성만 한다 |

## 7. 서비스를 닫을 때

이용약관 「서비스의 변경 및 중단」은 중단하기 전에 서비스 화면에 공지하고, 종료하면 개인정보를 개인정보처리방침에 따라 파기한다고 약속한다. 그래서 아래 차례를 지킨다.

1. **종료일을 정하고 미리 공지한다.** 지금 화면에는 공지를 띄우는 기능이 없다. 공지 문구를 보여 주는 FE 변경이 먼저다.
2. **남길 숫자가 있으면 먼저 뽑는다.** `deploy/sql/usage-metrics.sql` 은 개인정보 없이 숫자와 캠페인 이름만 낸다(deploy/README.md 「이용 지표 집계」). 사람을 알아볼 수 있는 자료는 내려받지 않는다.
3. **배포가 다시 돌지 않게 한다.** `gh workflow disable backend-deploy.yml -R family-fitness/family-fitness-be`
4. **DB 를 지운다.** 서버에서 `cd /opt/familyfitness && docker compose down -v`. `-v` 가 있어야 DB 볼륨(`familyfitness_pgdata`)까지 지워진다.
5. **DB 가 든 사본을 모두 지운다.** 서버 디스크 스냅숏에는 DB 가 그대로 들어 있어서, 남겨 두면 파기한 것이 아니다.
   ```bash
   P="--profile ff-backend"
   aws lightsail get-instance-snapshots $P --query 'instanceSnapshots[].name'          # 손으로 찍은 스냅숏
   aws lightsail delete-instance-snapshot --instance-snapshot-name <이름> $P
   aws lightsail get-auto-snapshots --resource-name ff-backend $P                     # 자동 스냅숏
   aws lightsail delete-auto-snapshot --resource-name ff-backend --date <YYYY-MM-DD> $P
   aws lightsail disable-add-on --resource-name ff-backend --add-on-type AutoSnapshot $P
   ```
   `pg_dump` 로 받아 둔 파일이 있으면 그것도 지운다.
6. **서버를 지운다.** `aws lightsail delete-instance --instance-name ff-backend --profile ff-backend` 뒤에 `aws lightsail release-static-ip --static-ip-name ff-backend-ip --profile ff-backend`. 서버 로그(journald, 30일 보관)도 서버와 함께 사라진다. `ff-frontend`, `ff-ai`, CloudFront 배포도 같은 방식으로 지운다.
7. **키와 계정을 정리한다.** 구글 OAuth 클라이언트, LLM 키, 저장소 Secrets 를 지우고, 공공데이터포털 활용신청은 필요 없으면 해지한다.
8. **기록을 남긴다.** 언제 무엇을 지웠는지(DB 볼륨, 스냅숏 이름, 서버) 날짜와 함께 적어 둔다.

## 8. 남은 일

- 아직 없는 기능은 [api-contract.md](./api-contract.md) 9장에 있다(프로필 사진, 앱 밖 푸시 알림, 미션 고치기 등).
- FE 에는 보호자 체력 나이 화면(FE #164)이 열려 있다. BE 의 체력 나이 API(#54, #58)는 운영에 올라가 있다.
- 기상청 인증키가 운영에서 정상으로 응답하는지 확인해야 한다(6장).
