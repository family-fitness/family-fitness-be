# backend

우리가족 체력키움 API 서버. Kotlin · Spring Boot 4.1 · Spring Modulith · Java 25.

## 실행

```bash
./gradlew bootRun                    # local 프로필: H2 인메모리 + 샘플 시드 + 개발 로그인 + AI 스텁. 아무것도 설치할 필요 없다
open http://localhost:8080/swagger-ui.html
```

| 명령 | 하는 일 |
|---|---|
| `./gradlew bootRun --args='--spring.profiles.active=compose'` | Docker Compose 의 PostgreSQL 로 실행 (`compose.yaml`) |
| `./gradlew test` | 전체 테스트. Docker 없이 H2 로 돈다. PostgreSQL 마이그레이션 검증(`PostgresMigrationTests`)은 Docker 가 있을 때만 |
| `./gradlew spotlessApply` | ktlint 포맷 교정. 커밋 전에 돌린다 (CI 는 `spotlessCheck`) |

JDK 25 는 Gradle 툴체인(foojay)이 자동으로 내려받는다.

### 프로필

| 프로필 | DB | 로그인 | AI |
|---|---|---|---|
| `local` (기본) | H2 인메모리, PostgreSQL 모드. `/h2-console` | 토큰 없으면 데모 부모 자동 인증 · dev-login · 구글 | 스텁 |
| `compose` | Docker Compose PostgreSQL | 자동 인증 · dev-login · 구글 | 스텁 (`APP_AI_MODE=http` 로 전환) |
| `prod` | `SPRING_DATASOURCE_*` 환경변수 | 구글만 | http (`APP_AI_BASE_URL`) |

운영 필수 환경변수: `APP_JWT_SECRET`(32자 이상), `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `APP_FRONTEND_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS`.

## 프론트 연동 — 로컬 실행 안내

**필요한 것은 인터넷 연결 하나다.** Docker · JDK · DB 설치 불필요. JDK 25 는 첫 실행 때 Gradle 이 자동으로 내려받는다(수 분).

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

로컬(기본 `local` 프로필)에서 시연을 위해 다음이 켜져 있다. 운영(`prod`)에서는 전부 꺼진다.

1. **로그인 없이 동작한다.** `Authorization` 헤더가 없으면 시드의 데모 부모(`demo-parent`, userId `…0001`)로 인증된다.
   다른 계정이 되려면 `X-Dev-User-Id: 00000000-0000-4000-8000-000000000002`(아직 가족이 없는 두 번째 부모) 헤더를 보낸다.
   로그인 화면을 붙일 때는 `POST /api/v1/auth/dev-login {"providerUserId":"demo-parent"}` 로 토큰을 받아
   `Authorization: Bearer <accessToken>` 을 보내면 된다. 운영에서는 `POST /api/v1/auth/google` 이 같은 응답을 준다.
2. **CORS 전부 허용.** 어느 포트·호스트에서 불러도 막지 않는다 (`app.cors.allowed-origins=*`).
3. **AI 서버는 스텁.** `family-fitness-ai` 는 아직 `/v1/*` 가 없으므로 코치 제안·대화·예측을 결정적인 가짜 응답으로 낸다
   (`app.ai.mode=stub`). AI 서버가 생기면 `--app.ai.mode=http --app.ai.base-url=http://…:8000` 으로 바꾸면 된다.
   AI 주소가 죽어 있어도 코치 제안은 영상 라벨·규준 근거로 대체 편성되어 빈 화면이 나지 않는다.
4. **시드 데이터.** 데모 가족 「데모네」(데모 엄마 PARENT·FULL, 데모 첫째 CHILD 11세·측정 1회 있음, 데모 아빠 PARENT 미연결·초대코드 `K7M2QT`),
   국민체력100 규준(실제 공공데이터), 샘플 영상 5편. 서버를 재시작하면 H2 인메모리라 시연 중 만든 데이터는 사라지고 시드만 남는다.

자주 쓰는 첫 호출:
```bash
curl localhost:8080/api/v1/me                                                        # nextStep HOME · 내 프로필
curl localhost:8080/api/v1/families/00000000-0000-4000-8000-000000000010/fitness-map # 홈 화면 한 번에
curl localhost:8080/api/v1/families/00000000-0000-4000-8000-000000000010/profiles    # 구성원
curl -X POST localhost:8080/api/v1/families/00000000-0000-4000-8000-000000000010/coach/runs -H 'Content-Type: application/json' -d '{}'
```
실패는 항상 `{"error": {"code": "...", "message": "..."}}`. `code` 로 분기하고 `message` 는 화면에 그대로 띄우지 않는다.

## 모듈

모듈 = 패키지 = ERD 묶음. 각 모듈은 `domain` · `application` · `adapter` 계층을 두고, 밖으로는 `api` 패키지만 연다
([docs/architecture.md](../docs/architecture.md)).

| 모듈 | 범위 | 엔드포인트 |
|---|---|---|
| `identity` | 계정 · 가족 · 프로필 · 동의 · 초대 · 응원 | auth/google · auth/refresh · auth/dev-login · me · families · profiles · invite · claim · support-mode · consent · cheers |
| `fitness` | 측정 항목 · 측정 등록(백분위 굳힘) · 결과 · 예측 | fitness/items · fitness-tests · fitness-tests/latest · predictions |
| `activity` | 일별 활동(걸음 · 타이머 · 영상 완주 분) | (웹 엔드포인트 없음 — coaching 이 사용) |
| `coaching` | 영상 · 주간 코치(승인 게이트) · 미션 · 대화 · 주간 요약 | coach/runs · approve · reject · missions · activity/steps · activity/timer · participants/confirm · videos · favorite · progress · coach/chat · report/weekly |

범위 밖: `GET /api/v1/facilities` (공공데이터 출처 미확정).

## AI 서비스

`shared.ai.AiGateway` 하나로 FastAPI(`family-fitness-ai`)를 부른다. `app.ai.mode=stub` 이면 AI 없이 결정적 가짜 응답으로
승인 게이트·대화·예측 흐름을 끝까지 돌릴 수 있다. 실제 AI 서비스는 아직 `/healthz` · `/readyz` 만 있고 `/v1/*` 는 미구현이라
local 기본값이 `stub` 이다. AI 가 서비스 테이블에 쓰는 경로는 없다.

## 테스트

- 도메인 단위 테스트(순수 Kotlin) → 애플리케이션 테스트(포트 대역) → 웹 테스트(MockMvc, H2 위 실제 스택) 순서로 둔다.
- `ModularityTests` 가 Spring Modulith 경계(순환 · 내부 패키지 접근 · 선언된 의존)를 검증한다.
- 규준표(`fitness_norms`)는 국민체력100 공공데이터 2024-07~2026-07 전수에서 AI 팀이 낸 분위수 산출물(`family-fitness-ai/data/release/age_band_value_quantiles.csv`)을 `V3__fitness_norms_kspo_2024_2026.sql` 로 적재한다. API 키가 필요 없다. 산출물이 갱신되면 `scripts/kspo_norms_to_sql.py` 로 다시 만든다. 데이터가 있는 구간: 유아기 48~83개월 · 유소년 11~12세 · 청소년 13~18세 · 성인 19~64세. 만 7~10세는 공공데이터에 측정이 없어 백분위가 `null` 이다.
- 현재 209개 테스트(도메인 · 애플리케이션 · MockMvc 웹 · AI HTTP 어댑터 · 모듈 경계). `PostgresMigrationTests` 는 Docker 없으면 건너뛴다.
