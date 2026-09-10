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
| `local` (기본) | H2 인메모리, PostgreSQL 모드. `/h2-console` | `POST /api/v1/auth/dev-login` + 구글 | 스텁 |
| `compose` | Docker Compose PostgreSQL | dev-login + 구글 | 스텁 (`APP_AI_MODE=http` 로 전환) |
| `prod` | `SPRING_DATASOURCE_*` 환경변수 | 구글만 | http (`APP_AI_BASE_URL`) |

운영 필수 환경변수: `APP_JWT_SECRET`(32자 이상), `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `APP_FRONTEND_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS`.

## 프론트 연동

1. 로컬에서는 구글 없이 시작한다. 데모 가족이 시드로 들어 있어 가입 흐름 없이 바로 홈부터 볼 수 있다.
   ```bash
   # 데모 부모 (가족 「데모네」: 데모 엄마 PARENT · 데모 첫째 CHILD 유소년 · 데모 아빠 PARENT 미연결)
   curl -s localhost:8080/api/v1/auth/dev-login -H 'Content-Type: application/json' -d '{"providerUserId":"demo-parent"}'
   # → {"accessToken":"...","refreshToken":"...","userId":"...","nextStep":"HOME","profiles":[...]}

   # 새 계정으로 가입 흐름부터 보려면 아무 providerUserId 나 쓴다 → nextStep CREATE_FAMILY
   curl -s localhost:8080/api/v1/auth/dev-login -H 'Content-Type: application/json' -d '{"providerUserId":"parent-1"}'
   # 초대 흐름: 두 번째 계정으로 dev-login 뒤 POST /api/v1/profiles/claim {"claimCode":"K7M2QT"} → 데모 아빠 프로필 연결
   ```
   운영에서는 `POST /api/v1/auth/google` 에 구글 인가코드와 redirectUri 를 보낸다. 응답 모양은 같다.
2. 이후 모든 요청에 `Authorization: Bearer <accessToken>`. 만료되면 `POST /api/v1/auth/refresh {refreshToken}`.
   앱 진입 시 `GET /api/v1/me` 로 `nextStep`(`CREATE_FAMILY` · `CLAIM` · `HOME`)과 프로필 목록을 받는다.
3. 실패는 항상 `{"error": {"code": "...", "message": "..."}}`. `code` 로 분기하고 `message` 는 화면에 그대로 띄우지 않는다.
   엔드포인트별 코드는 [docs/api-contract.md](../docs/api-contract.md), 스키마는 Swagger UI 에 있다.
4. CORS 허용 출처 기본값은 `http://localhost:5173`, `http://localhost:3000` (`APP_CORS_ALLOWED_ORIGINS`).

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
