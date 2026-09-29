# backend

우리가족 체력키움 API 서버. Java · Spring Boot 4.1 · Spring Modulith · Java 25.

## 실행

```bash
./gradlew bootRun                    # local 프로필: H2 인메모리 + 시드 + 자동 로그인 + AI 스텁. Docker·DB 불필요
open http://localhost:8080/swagger-ui.html
```

| 명령 | 하는 일 |
|---|---|
| `./gradlew bootRun --args='--spring.profiles.active=compose'` | Docker Compose 의 PostgreSQL 로 실행 (`compose.yaml`) |
| `./gradlew test` | 전체 테스트. Docker 없이 H2 로 돈다. PostgreSQL 에서만 도는 시험(`PostgresMigrationTests` 등)은 Docker 가 있을 때만 돈다(CI 의 ubuntu 러너에서는 돈다) |
| `./gradlew spotlessApply` | palantir-java-format 포맷 교정. 커밋 전에 돌린다 (CI 는 `spotlessCheck`) |

Gradle 을 띄울 JDK(17 이상, 아무 버전)만 깔려 있으면 된다. 빌드에 쓰는 JDK 25 는 Gradle 툴체인(foojay)이 자동으로 내려받는다.

**IDE 에서 `FamilyfitnessApplication.main` 을 바로 돌릴 때는 활성 프로필 `local` 을 직접 준다.**
기본 프로필이 없어서, 프로필 없이 띄우면 개발용 설정(시드 · 자동 로그인 · dev-login · H2 콘솔)이 켜지지 않는다. 이때 `APP_JWT_SECRET` 이 없으면 기동이 멈춘다.
- IntelliJ: Run Configuration → Active profiles 에 `local`. 또는 VM options 에 `-Dspring.profiles.active=local`.
- 프로필을 주지 않아도 local 로 뜨는 것은 `./gradlew bootRun`(과 `bootTestRun`)뿐이다(`build.gradle` 의 BootRun 설정이 `spring.profiles.default=local` 을 준다).

### 프로필

| 프로필 | DB | 로그인 | AI | 시드 |
|---|---|---|---|---|
| `local` (`bootRun` 기본) | H2 인메모리, PostgreSQL 모드. `/h2-console` | 토큰 없으면 데모 부모 자동 인증 · dev-login · 구글 | 스텁 | 있음 |
| `compose` | Docker Compose PostgreSQL | 자동 인증 · dev-login · 구글 | 스텁 (`APP_AI_MODE=http` 로 전환) | 있음 |
| `test` (시험 전용) | H2 인메모리 | dev-login · 구글 | 스텁 | 있음 |
| `prod` | `SPRING_DATASOURCE_*` 환경변수 | 구글만 | http (`APP_AI_BASE_URL`) | 없음 |

- 개발용 기능(dev-login · 자동 로그인 · H2 콘솔)이 켜져 있는데 활성 프로필에 local · compose · test 가 없으면 기동 전에 멈춘다(`DevFeatureGuard`).
- 운영은 `SPRING_PROFILES_ACTIVE=prod` 로 띄운다. 운영 필수 환경변수: `APP_JWT_SECRET`(32자 이상), `SPRING_DATASOURCE_URL` · `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `APP_FRONTEND_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS`, `APP_AI_BASE_URL`.
- 편성 전용 스레드 풀 크기는 `APP_COACH_EXECUTOR_POOL_SIZE`(기본 8) · `APP_COACH_EXECUTOR_QUEUE_CAPACITY`(기본 무제한)로 바꾼다.
- 알림은 커밋 뒤 알림 전용 스레드 풀에서 쓴다: `app.notification.executor.pool-size`(2) · `app.notification.executor.queue-capacity`(1000). 정해진 시각에 도는 일의 스레드는 `spring.task.scheduling.pool.size`(2)다.
- 정해진 시각에 도는 일(모두 KST)은 설정으로 바꾼다: 리프레시 토큰 정리 `app.auth.refresh-token-cleanup.cron`(매일 04:00) · 알림 `app.notification.mission-ready-cron`(07:30) · `app.notification.remeasure-cron`(09:00) · 리그 정산 `app.league.settle-cron`(매월 1일 00:10). 표는 [docs/api-contract.md](../docs/api-contract.md) 0장.

## 프론트 연동 — 로컬 실행 안내

**필요한 것: Git · JDK 17 이상(아무 버전, Temurin 21 권장) · 인터넷.** Docker · DB · 환경변수는 필요 없다.
`./gradlew` 가 Gradle 을 띄우는 데 JDK 가 하나 있어야 하고, 빌드용 JDK 25 는 첫 실행 때 Gradle 이 자동으로 내려받는다(수 분).

```bash
git clone https://github.com/family-fitness/family-fitness-be.git
cd family-fitness-be/backend
./gradlew bootRun          # Windows: gradlew.bat bootRun
# "Started FamilyfitnessApplication" 이 뜨면 http://localhost:8080
```

| 주소 | 무엇 |
|---|---|
| `http://localhost:8080/api/v1/...` | API. 기본 경로는 [docs/api-contract.md](../docs/api-contract.md) |
| `http://localhost:8080/swagger-ui.html` | 요청·응답 스키마, 브라우저에서 바로 호출 |
| `http://localhost:8080/h2-console` | DB 내용 보기 (JDBC URL `jdbc:h2:mem:familyfitness`, 사용자 `sa`, 비밀번호 없음) |

로컬(`local` 프로필)에서 시연을 위해 다음이 켜져 있다. 운영(`prod`)에서는 전부 꺼진다.

1. **로그인 없이 동작한다.** `Authorization` 헤더가 없으면 시드의 데모 부모(`demo-parent`, userId `…0001`)로 인증된다.
   다른 계정이 되려면 `X-Dev-User-Id: 00000000-0000-4000-8000-000000000002`(아직 가족이 없는 두 번째 부모) 헤더를 보낸다. 값이 UUID 가 아니면 400 이다.
   로그인 화면을 붙일 때는 `POST /api/v1/auth/dev-login {"providerUserId":"demo-parent"}` 로 토큰을 받아
   `Authorization: Bearer <accessToken>` 을 보내면 된다. 운영에서는 `POST /api/v1/auth/google` 이 같은 응답을 준다.
   `{"providerUserId":"demo-fresh"}` 는 부를 때마다 가족 없는 **새 계정**(nextStep `CREATE_FAMILY`)을 만든다 — FE 로그인 화면의 「새 계정」 단추라,
   서버를 다시 띄우지 않고도 가족 만들기부터 몇 번이고 볼 수 있다. 다른 값은 같은 값이면 같은 계정이다.
   리프레시 토큰은 한 번만 쓸 수 있다(회전). refresh 응답의 새 `refreshToken` 을 저장하고, 로그아웃할 때 `POST /api/v1/auth/logout` 을 부른다.
2. **CORS 전부 허용.** 어느 포트·호스트에서 불러도 막지 않는다 (`app.cors.allowed-origins=*`).
   초대 링크(`shareUrl`)는 FE 개발 서버 주소 `http://localhost:3000` 으로 만든다.
3. **AI 서버는 스텁.** AI 서비스 없이도 뜨도록 코치 제안·대화를 결정적인 가짜 응답으로 낸다(`app.ai.mode=stub`). 스텁 제안도 실제 클립 경계로 칸을 낸다.
   실제 AI 에 붙이는 방법은 아래 「AI 서비스」 절. AI 주소가 죽어 있어도 코치 제안은 영상 구간 표(`V132`)의 라벨로 대체 편성되어 빈 화면이 나지 않는다.
4. **시드 데이터.** 데모 가족 「데모네」(데모 엄마 PARENT·FULL, 데모 첫째 CHILD·측정 1회 있음, 데모 아빠 PARENT 미연결·초대코드 `K7M2QT`),
   데모 첫째 · 엄마의 운동할 수 있는 시간, 국민체력100 규준(실제 공공데이터), AI 운동 영상 48편 · 구간 695개(`V132`, 운영에도 들어간다), 시험용 가짜 영상 4편(`sample00002~5`).
   서버를 재시작하면 H2 인메모리라 시연 중 만든 데이터는 사라지고 시드만 남는다.

자주 쓰는 첫 호출:
```bash
FAMILY=00000000-0000-4000-8000-000000000010
CHILD=00000000-0000-4000-8000-000000000012   # 데모 첫째
curl localhost:8080/api/v1/me                                   # nextStep HOME · 내 프로필 · selfProfileId
curl localhost:8080/api/v1/families/$FAMILY/fitness-map         # 홈 화면 한 번에
curl localhost:8080/api/v1/families/$FAMILY/profiles            # 구성원
curl -X POST localhost:8080/api/v1/families/$FAMILY/coach/runs -H 'Content-Type: application/json' \
     -d "{\"profileId\":\"$CHILD\",\"date\":\"$(date +%F)\",\"minutes\":15}"   # 데모 첫째의 오늘 편성(202)
curl "localhost:8080/api/v1/families/$FAMILY/coach/runs/latest?profileId=$CHILD"  # 그 편성의 결과(칸 포함)
curl localhost:8080/api/v1/profiles/$CHILD/progress             # 레벨 · 경험치 · 업적
curl "localhost:8080/api/v1/notifications?profileId=$CHILD"     # 데모 첫째의 알림함(보호자가 계정 없는 아이 대신)
```
실패는 항상 `{"error": {"code": "...", "message": "..."}}`. `code` 로 분기하고 `message` 는 화면에 그대로 띄우지 않는다.

지금 FE 가 부르는 이름 `rest-days` · `/sessions/{seq}/done` · `/clips` 는 계약 이름(`rest-cards` · `/complete` · `/exercises`)의 **전환기 별칭**이라
같은 핸들러가 답한다(Swagger 에는 deprecated). FE 가 계약 이름으로 옮기면 걷는다 — 목록은 [api-contract.md 「전환기 별칭」](../docs/api-contract.md).

## 모듈

모듈 = 패키지 = ERD 묶음. 각 모듈은 `domain` · `application` · `adapter` 계층을 두고, 밖으로는 `api` 패키지만 연다
([docs/architecture.md](../docs/architecture.md)). 주소 앞에는 `/api/v1` 이 붙는다.

| 모듈 | 범위 | 주소 |
|---|---|---|
| `identity` | 계정 · 가족 · 프로필 · 동의 · 초대 · 응원 · 운동할 수 있는 시간 | `auth/google` · `auth/refresh` · `auth/logout` · `auth/dev-login` · `me` · `families` · `families/{id}/profiles` · `profiles/{id}` · `profiles/{id}/support-mode` · `profiles/{id}/consent` · `profiles/{id}/invite` · `invites/{code}` · `profiles/claim` · `profiles/{id}/availability` · `families/{id}/cheers` |
| `fitness` | 측정 항목 · 측정 등록(백분위 굳힘) · 결과 · 이력 · 가족 체력 지도 | `fitness/items` · `profiles/{id}/fitness-tests` · `profiles/{id}/fitness-tests/latest` · `families/{id}/fitness-map` |
| `activity` | 일별 활동(초 단위) · 쉬는 날 카드 | `families/{id}/rest-cards` · `families/{id}/rest-cards/{restDate}` |
| `progress` | 경험치 원장 · 레벨 · 업적 · 이어서 한 날 | `profiles/{id}/progress` |
| `coaching` | 하루 편성(승인 게이트) · 미션과 칸 · 칸 끝 · 운동 느낌 · 캘린더 · 운동 구간 · 영상 · 대화 · 주간 요약 | `families/{id}/coach/runs` · `families/{id}/coach/runs/latest` · `coach/runs/{id}` · `coach/runs/{id}/approve` · `coach/runs/{id}/reject` · `families/{id}/missions` · `missions/{id}` · `missions/{id}/sessions/{seq}/complete` · `missions/{id}/feedback` · `missions/{id}/participants/{profileId}/confirm` · `missions/{id}/activity/steps` · `missions/{id}/activity/timer` · `families/{id}/calendar` · `exercises` · `exercises/{id}/favorite` · `videos` · `videos/{id}/favorite` · `videos/{id}/progress` · `coach/chat` · `families/{id}/report/weekly` |
| `league` | 가족 리그(월 단위 달성률 · 다섯 티어 · 월초 정산) | `families/{id}/league` |
| `notification` | 알림함(응원 · 새 운동 · 업적 · 다시 재기) | `notifications` · `notifications/read` |

경로 45개(메서드까지 52개, 전환기 별칭은 세지 않음). 범위 밖: `GET /api/v1/facilities` (공공데이터 출처 미확정).
FE 가 부르지 않는 주소 8개: coach/chat · report/weekly · videos 셋 · activity/steps · activity/timer · participants/{profileId}/confirm. 걷을지는 결정을 기다린다.

## AI 서비스

`shared.ai.AiGateway` 하나로 FastAPI(`family-fitness-ai`)의 `{app.ai.base-url}/v1` 을 부른다. AI 는 `/v1` 아래 다섯 주소
(fitness/assessment · fitness/trajectory · videos/search · coach/runs · coach/messages)와 헬스 체크 `/health` 를 연다.
서버는 fitness/trajectory 를 부르지 않는다(10년 예측을 걷었다, `V153`). AI 가 서비스 테이블에 쓰는 경로는 없다.

- `app.ai.mode=stub` 이면 AI 없이 결정적 가짜 응답으로 승인 게이트·대화 흐름을 끝까지 돌릴 수 있다. prod 밖에서는 기본값이다(`APP_AI_MODE` 로 바꾼다).
- 실제 AI 에 붙이기
  1. AI 저장소 README 순서대로 가상환경 · `.env`(LLM 키) · 임베딩 서버를 준비하고 `make serve` 로 띄운다(`http://127.0.0.1:8000`).
  2. `./gradlew bootRun --args='--app.ai.mode=http'` — `app.ai.base-url` 기본값이 `http://localhost:8000` 이다.
- 편성은 아이 한 명의 하루다. 서버는 AI 에 편성 대상 한 명만 보내고, AI 의 클립 단위 응답(`phase · order · duration_sec · video`)을 제안 칸으로 옮긴다.
  칸마다 분은 서버가 나눈다(준비 · 정리 1분씩, 남은 분은 본운동 칸에). `focus_factor`(부모가 고른 키울 힘)는 AI 가 대상 요인으로 쓴다(AI 저장소 `feature/AI-14-focus-factor`). `with_companion` 은 AI 가 아직 모르는 칸이라 무시한다.
- AI 가 연결 실패 · 시간 초과 · 5xx 이거나 실행 단위로 실패하면(failed · 폴링 만료 · 404) 대체 편성으로 넘어간다. 실패 까닭은 응답의 `failureCode` 다.
  요청 · 응답 모양과 결과 처리 표는 [docs/api-contract.md](../docs/api-contract.md) 8장.

## 데이터 스크립트 (`scripts/`)

Python 3 만 있으면 된다. Windows 콘솔(cp949)에서 결과를 파일로 받을 때는 `PYTHONIOENCODING=utf-8` 을 앞에 붙인다(`ddl_to_dbml.py` 는 스스로 UTF-8 로 쓴다).

| 스크립트 | 하는 일 | 실행 |
|---|---|---|
| `kspo_norms_to_sql.py` | AI 팀 분위수 산출물(`age_band_value_quantiles.csv`) → 규준표 마이그레이션(`V3`). 적용된 V3 를 다시 적재하는 방식은 아직 정하지 않았다 | backend 폴더에서 `python3 scripts/kspo_norms_to_sql.py ../../family-fitness-ai/data/release/age_band_value_quantiles.csv 2026 > src/main/resources/db/migration/V3__fitness_norms_kspo_2024_2026.sql` |
| `ai_clips_to_sql.py` | AI 클립 릴리스(`video_clips.csv` · `clip_labels.csv` · `corpus_meta.csv`) → 운동 영상 · 구간 마이그레이션. 첫 적재는 `--create-table`(`V132`), 다음 릴리스는 새 V 파일 | backend 폴더에서 `python3 scripts/ai_clips_to_sql.py ../../family-fitness-ai > src/main/resources/db/migration/V<다음 번호>__coaching_clip_release_<AI 커밋>.sql` |
| `ddl_to_dbml.py` | 마이그레이션 DDL → `docs/erd.dbml` | 저장소 루트에서 `python3 backend/scripts/ddl_to_dbml.py > docs/erd.dbml` |

`ddl_to_dbml.py` 는 파일을 버전 차례로, 파일 안에서는 문장 차례로 적용한다. `create table` · `create [unique] index` ·
`alter table … add/drop/rename column · alter column … set/drop not null · add/drop constraint` 를 읽고, 뒤 마이그레이션이 걷은 FK · 제약 · 인덱스는 ERD 에서도 뺀다.
insert · update · delete · select 는 건너뛴다. 그 밖의 DDL 을 만나면 ERD 를 내지 않고 그 문장을 stderr 에 적고 1로 끝난다. 그런 마이그레이션을 더했으면 스크립트를 먼저 고친다.

## 테스트

- 도메인 단위 테스트(순수 Java) → 애플리케이션 테스트(포트 대역) → 웹 테스트(MockMvc, H2 위 실제 스택) 순서로 둔다.
- `ModularityTests` 가 모듈 일곱 개가 있는지와 Spring Modulith 경계(순환 · 내부 패키지 접근 · 선언된 의존)를 검증하고, ArchUnit 으로 모듈마다 부를 수 있는 모듈 목록(아무도 notification 을 부르지 않는다 등)을 확인한다.
- `FamilyfitnessApplicationTests` 가 H2 에 전체 마이그레이션을 적용해 기동을 확인한다. `PostgresMigrationTests` 등 Testcontainers 시험은 Docker 가 없으면 건너뛴다.
- 규준표(`fitness_norms`)는 국민체력100 공공데이터 2024-07~2026-07 전수에서 AI 팀이 낸 분위수 산출물(`family-fitness-ai/data/release/age_band_value_quantiles.csv`)을 `V3__fitness_norms_kspo_2024_2026.sql` 로 적재한다. API 키가 필요 없다. 산출물이 갱신되면 `scripts/kspo_norms_to_sql.py` 로 다시 만든다. 데이터가 있는 구간: 유아기 48~83개월 · 유소년 11~12세 · 청소년 13~18세 · 성인 19~64세. 만 7~10세는 공공데이터에 측정이 없어 백분위가 `null` 이다.

## 마이그레이션 번호

- 순번을 쓴다. 지금 마지막은 `V153` 이고 다음은 `V154` 부터다(V1 · V2 · V3 다음이 V130 이다).
- `V148` 은 비어 있다. V149 가 이미 적용됐으므로 V148 을 새로 쓰면 안 된다(아래 `outOfOrder` 때문에 검증에 실패한다).
- Flyway `outOfOrder` 가 꺼져 있다. 번호가 낮은 파일이 나중에 머지되면 검증에 실패하니, 마이그레이션이 있는 PR 은 번호 차례대로 머지한다.
- 적용된 버전 마이그레이션은 고치지 않는다. 바꿀 것이 있으면 새 V 파일을 만든다.
- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 문법만 쓴다.
- 마이그레이션을 더하면 `ddl_to_dbml.py` 로 `docs/erd.dbml` 을 다시 만든다.
