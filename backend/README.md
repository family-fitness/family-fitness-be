# backend

우리가족 체력키움 API 서버. Java · Spring Boot 4.1 · Spring Modulith · Java 25.

## 실행

```bash
./gradlew bootRun                    # local 프로필: H2 인메모리 + 시드 + 개발용 로그인 + AI 스텁. Docker·DB 불필요
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

| 프로필 | DB | 로그인 | AI | 시드 | actuator | API 문서(Swagger UI · `/v3/api-docs`) |
|---|---|---|---|---|---|---|
| `local` (`bootRun` 기본) | H2 인메모리, PostgreSQL 모드. `/h2-console` | `X-Dev-User-Id` 헤더 인증 · dev-login · 심사용 계정 · 구글 | 스텁 | 있음 | health · info · modulith | 켬(로그인 없이) |
| `compose` | Docker Compose PostgreSQL | `X-Dev-User-Id` 헤더 인증 · dev-login · 심사용 계정 · 구글 | 스텁 (`APP_AI_MODE=http` 로 전환) | 있음 | health · info · modulith | 켬(로그인 없이) |
| `test` (시험 전용) | H2 인메모리 | dev-login · 구글 | 스텁 | 있음 | health · info · modulith | 켬(로그인 없이) |
| `prod` | `SPRING_DATASOURCE_*` 환경변수 | 구글 · 심사용 계정(`APP_AUTH_REVIEW_LOGIN_ENABLED`, 기본 켬 · `APP_AUTH_REVIEW_LOGIN_UNTIL`, 기본 2026-10-31 까지) | http (`APP_AI_BASE_URL`) | 없음 | health 만 | 끔(404) |

- 개발용 기능(dev-login · 자동 로그인 · H2 콘솔 · 시간 이동)이 켜져 있는데 활성 프로필에 local · compose · test 가 없으면 기동 전에 멈춘다(`DevFeatureGuard`). 활성 프로필에 prod 가 있으면 local · compose · test 가 함께 있어도 멈춘다 — `SPRING_PROFILES_ACTIVE=prod,compose` 로 띄우면 compose 파일이 켠 시간 이동(`POST /api/v1/dev/clock`, 인증 없음) · `X-Dev-User-Id` 자동 로그인이 운영에서 켜지기 때문이다.
- actuator: `/actuator/health` 는 로그인 없이 부른다. info · modulith(모듈 · 패키지 구조와 모듈 사이 의존)는 로그인해야 보이는데, 운영에서는 심사용 로그인으로 누구나 토큰을 받으므로 prod 는 health 만 연다(`application-prod.properties` 의 `management.endpoints.web.exposure.include=health`, 시험 `ProdProfileWebTest`). 나머지 주소는 404 다.
- API 문서: Swagger UI(`/swagger-ui.html`) · `/v3/api-docs` 는 로그인 없이 열리는 주소라(`SecurityConfig`), prod 는 끈다(`springdoc.api-docs.enabled=false` · `springdoc.swagger-ui.enabled=false`, 시험 `ProdProfileWebTest`). 켜 두면 심사용 로그인을 포함한 모든 경로와 요청 · 응답 모양이 바깥에 나간다. 운영 서버의 API 모양은 local · compose 로 띄워 보거나 `docs/api-contract.md` 로 본다.
- **심사용 계정 로그인**(`POST /api/v1/auth/review-login`, `app.auth.review-login.enabled`)은 개발용 기능이 아니라 이 목록에 없다.
  심사위원이 운영 서버에서 구글 계정 없이 둘러보는 길이라 운영에서 켜 둔다(`APP_AUTH_REVIEW_LOGIN_ENABLED`, 기본 `true` — 심사가 끝나면 `false`).
  **끝나는 날**: prod 는 `app.auth.review-login.until`(기본 `2026-10-31`, 환경변수 `APP_AUTH_REVIEW_LOGIN_UNTIL=YYYY-MM-DD`)까지만 받는다.
  날짜는 Asia/Seoul 기준이고 그날까지 받는다. 다음 날부터는 켜져 있어도 꺼진 것과 같게 404 `NOT_FOUND` 이고 계정을 만들지 않는다 — 끄는 것을 잊어도
  누구나 계정을 만드는 길이 열려 있지 않게. 심사 일정이 바뀌면 이 환경변수로 옮기고 다시 띄운다. local · compose 는 끝나는 날이 없다.
  부를 때마다 새 계정과 체험 가족(엄마 · 아빠 · 하윤 만 11세 · 서준 만 6세, 두 아이는 사흘 전 측정 있음, 하윤 인증 2등급)을 만들 뿐
  남의 계정이 될 수 없다. 같은 IP(IPv6 는 /56 대역 — 통신사가 집 한 곳에 주는 크기)에서 한 시간에 30번을 넘기면 429 `TOO_MANY` 다.
  새 계정은 IP 와 상관없이 모두 합쳐 한 시간에 300개까지 만든다(`app.auth.review-login.max-total`). 그 뒤로도 429 는 주지 않는다 — 누구
  한 사람이 300개를 채워 모든 심사위원을 막지 못하게. 대신 그 IP 가 이 한 시간에 만든 계정이 있으면 그 가운데 가장 최근 것으로 들이고,
  없으면 새 계정을 하나 만든다(그 뒤로 그 IP 는 그 계정으로 들어온다). **다시 받은 계정은 같은 IP(같은 와이파이 · 회사망)에서 먼저 들어온
  사람도 토큰을 쥐고 있다** — 그 사람이 가족 이름 · 식구 · 기록을 바꾸면 보이고, 내가 하는 일도 그 사람에게 보인다. 다른 IP 가 만든 계정은
  주지 않는다: 한도를 채운 사람의 계정을 받으면 그 사람이 꾸민 화면을 보고 내 일을 들킨다.
  **운영에서는 늘 프록시 뒤다** — FE 가 `/api/v1/**` 를 Next 서버(rewrites)를 거쳐 넘기므로 BE 가 보는 remoteAddr 는 누가 부르든 Next 서버 IP 다.
  그래서 prod 프로필은 `server.forward-headers-strategy=native` 를 켜 두고, Tomcat 이 믿을 프록시(기본: 루프백 · 사설망)가 붙인 `X-Forwarded-For` 에서
  브라우저 IP 를 꺼낸다. 배포할 때 확인할 것:
  - Next 는 받은 `X-Forwarded-For` 를 그대로 넘길 뿐 스스로 붙이지 않는다(next 16 rewrites 의 proxy-request). Next 앞의 HTTPS 프록시(nginx 등)가 `X-Forwarded-For` 에 브라우저 IP 를 붙여야 한다.
  - Next 서버가 BE 에 공인 IP 로 들어오면 `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` 에 그 주소(정규식)를 준다. 안 주면 헤더를 믿지 않고 모두 Next 서버 IP 하나로 센다.
  - 둘 중 하나가 빠져도 전체 한도(한 시간 300개)가 있어 계정이 끝없이 쌓이지는 않는다. 다만 헤더가 오지 않으면 모든 심사위원이 Next 서버 IP 하나로 세져,
    누구든 31번만 부르면 한 시간 동안 모든 심사위원이 429 를 받는다. 거꾸로 앞 프록시 없이 Next 를 바로 열면 브라우저가 꾸며 보낸 `X-Forwarded-For` 가
    그대로 넘어와(Next 는 사설망이라 Tomcat 이 믿는다) IP 한도가 뜻이 없어진다. 코드로는 막을 수 없어 아래 점검으로 확인한다.
  체험 가족은 실제 가족의 리그 방에 넣지 않는다. 리그를 열면 체험 가족 + 가짜 가족 일곱의 체험 방을 받는다. 가짜 가족의 이름 · 달성률 · 점수는
  체험 가족 id 로 정해져 늘 같다. 이 방은 DB 에 적지 않아 월초 정산에도 들어가지 않는다(`league.application.TrialLeague`).

  **심사 기간 전 배포 점검 — 심사용 로그인 IP**
  1. [ ] Next 앞에 HTTPS 프록시(nginx 등)가 있고, 그 프록시가 `X-Forwarded-For` 에 브라우저 IP 를 붙인다
     (nginx `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`, 브라우저가 보낸 값을 아예 버리려면 `$remote_addr`).
  2. [ ] 바깥망(휴대폰 LTE 등)에서 가짜 헤더를 실어 한 번 부른다(심사용 계정이 하나 생긴다):
     `curl -s -o /dev/null -w '%{http_code}
' -X POST -H 'X-Forwarded-For: 203.0.113.9' https://<FE 주소>/api/v1/auth/review-login`
     그리고 BE 로그의 `심사용 계정 로그인: IP …` 줄을 본다. IP 는 끝자리를 가린 값만 찍힌다 — IPv4 는 마지막 옥텟이 `*`(예 `198.51.100.*`),
     IPv6 는 /56 대역(예 `2001:db8:abcd:1200::/56`)이다.
     - 부른 기기의 공인 IP 앞 세 옥텟(IPv6 는 /56 대역)이 찍히면 된다(앞 프록시가 붙인 값을 Tomcat 이 오른쪽부터 읽어 꾸민 값은 버린다).
     - `203.0.113.*` 가 찍히면 꾸민 헤더를 믿고 있다 — 1 을 고친다.
     - Next 서버 IP(사설망 · 루프백, 예 `10.0.0.*` · `127.0.0.*`)가 찍히면 헤더가 오지 않는다 — 1 을 고치거나, BE 가 Next 를 공인 IP 로 받는다면 `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` 를 준다.
  심사용 계정은 편성(AI · LLM)을 하루(KST) 20번까지 시작한다(`ReviewRunQuota`, 21번째는 429 `TOO_MANY`). 구글 계정은 세지 않는다.
  계정은 누구나 만들 수 있어 계정마다 한도만으로는 LLM 호출이 쌓이므로, 심사용 계정을 모두 합쳐 하루 400번 AI 로 짠 뒤로는 AI 를 부르지 않고
  라벨 대체 편성으로 짠다. 429 로 막지 않는 까닭: 누구 한 사람이 한도를 채우면 그날 모든 심사위원의 편성이 막힌다.
  코치 대화(`POST /coach/chat`)도 대화마다 LLM 이 불릴 수 있어 센다(`ReviewChatQuota`): 심사용 계정마다 하루(KST) 30번, 모두 합쳐 하루 300번.
  넘기면 AI 를 부르기 전에 429 `TOO_MANY` 다. 편성과 달리 모두 합친 한도도 429 로 막는다 — AI 에 LLM 없이 답하라고 부탁할 칸이 없고, FE 에
  대화 화면이 없어 막혀도 심사위원이 보는 곳이 없다.
  **만든 심사용 계정 · 체험 가족을 지우는 작업은 없다**(계정 지우기 기능 자체가 아직 없다). 쌓이는 양은 한 시간에 300가족에, 한도가 찬 뒤로는 IP(IPv6 /56) 하나마다 한 가족씩 더해진 만큼이다.
  심사가 끝나는 날 `APP_AUTH_REVIEW_LOGIN_ENABLED=false` 로 끄고(잊어도 끝나는 날 다음 날부터는 404 다), 남은 줄은 `users.provider = 'REVIEW'` 로 골라 치운다.
  test 프로필은 꺼 두고, 켜는 시험(`ReviewLoginApiTest`)만 켠다.
- 운영은 `SPRING_PROFILES_ACTIVE=prod` 로 띄운다. 운영 필수 환경변수: `APP_JWT_SECRET`(32자 이상), `SPRING_DATASOURCE_URL` · `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `APP_FRONTEND_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS`, `APP_AI_BASE_URL`.
  `SPRING_DATASOURCE_*` · `APP_JWT_SECRET` · `APP_FRONTEND_BASE_URL`(http(s):// 로 시작하는 FE 주소, 초대 링크 앞머리)은 빠뜨리면 기동이 멈춘다. `GOOGLE_*` · `APP_CORS_ALLOWED_ORIGINS` · `APP_AI_BASE_URL` 은 빠뜨려도 뜨지만 빈 값이나 개발용 기본값(localhost)으로 돌아 로그인 · 브라우저 요청 · AI 편성이 제대로 되지 않는다.
- 편성 전용 스레드 풀 크기는 `APP_COACH_EXECUTOR_POOL_SIZE`(기본 8) · `APP_COACH_EXECUTOR_QUEUE_CAPACITY`(기본 무제한)로 바꾼다.
- 알림은 커밋 뒤 알림 전용 스레드 풀에서 쓴다: `app.notification.executor.pool-size`(2) · `app.notification.executor.queue-capacity`(1000). 정해진 시각에 도는 일의 스레드는 `spring.task.scheduling.pool.size`(2)다.
- 정해진 시각에 도는 일(모두 KST)은 설정으로 바꾼다: 리프레시 토큰 정리 `app.auth.refresh-token-cleanup.cron`(매일 04:00) · 알림 `app.notification.mission-ready-cron`(07:30) · `app.notification.remeasure-cron`(09:00) · 리그 정산 `app.league.settle-cron`(매월 1일 00:10). 표는 [docs/api-contract.md](../docs/api-contract.md) 0장.

### 로그와 IP 보관

- BE 는 로그를 표준 출력으로만 낸다(파일에 쓰지 않는다). 보관은 띄운 환경(도커 로그 · systemd 저널 등)이 한다.
- 로그에 남는 IP 는 심사용 계정 로그인 한 줄(`심사용 계정 로그인: IP …`)뿐이고, 끝자리를 가린 값만 쓴다(IPv4 마지막 옥텟 `*`, IPv6 /56 대역,
  `ReviewLoginLimiter.maskedForLog`). 한도를 세는 온전한 IP 는 서버 메모리에만 한 시간 두고 버린다(재시작하면 빈다). DB 에는 IP 를 저장하지 않는다.
- **보관 방침**: 운영 로그는 **30일** 뒤 지운다. 띄우는 환경의 로그 로테이션 보관 기간을 30일로 맞춘다(예 logrotate `maxage 30`,
  journald `MaxRetentionSec=30day`). 심사가 끝나 심사용 로그인을 끈 뒤 30일이면 IP 가 든 줄이 모두 사라진다.
- 개인정보처리방침(FE `src/lib/legal.ts`)에는 아직 접속 기록 · IP 항목이 없다. 「심사용 계정 로그인 때 IP 앞자리를 서비스 운영 로그에 30일 남긴다」 를
  더할지 팀이 정한다(FE 쪽 작업).

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

1. **브라우저는 운영처럼 로그인부터 한다.** 토큰이 없으면 401 이라 FE 는 로그인 화면으로 간다. 로그인 화면의 개발용 계정 단추가
   `POST /api/v1/auth/dev-login {"providerUserId":"demo-parent"}` 로 토큰을 받아 `Authorization: Bearer <accessToken>` 을 보낸다.
   curl · 스크립트는 로그인 없이 `X-Dev-User-Id: <userId>` 헤더로 그 계정이 된다 — 데모 부모 `…0001`, 아직 가족이 없는 두 번째 부모 `…0002`.
   값이 UUID 가 아니면 400, 헤더가 없거나 비어 있으면 401 이다. 운영에서는 `POST /api/v1/auth/google` 이 같은 응답을 준다.
   `{"providerUserId":"demo-fresh"}` 는 부를 때마다 가족 없는 **새 계정**(nextStep `CREATE_FAMILY`)을 만든다 — FE 로그인 화면의 「새 계정」 단추라,
   서버를 다시 띄우지 않고도 가족 만들기부터 몇 번이고 볼 수 있다. 다른 값은 같은 값이면 같은 계정이다.
   리프레시 토큰은 한 번만 쓸 수 있다(회전). refresh 응답의 새 `refreshToken` 을 저장하고, 로그아웃할 때 `POST /api/v1/auth/logout` 을 부른다.
2. **CORS 전부 허용.** 어느 포트·호스트에서 불러도 막지 않는다 (`app.cors.allowed-origins=*`).
   초대 링크(`shareUrl`)는 FE 개발 서버 주소 `http://localhost:3000` 으로 만든다.
3. **AI 서버는 스텁.** AI 서비스 없이도 뜨도록 코치 제안·대화를 결정적인 가짜 응답으로 낸다(`app.ai.mode=stub`). 스텁 제안도 실제 클립 경계로 칸을 낸다.
   실제 AI 에 붙이는 방법은 아래 「AI 서비스」 절. AI 주소가 죽어 있어도 코치 제안은 영상 구간 표(`V132` 유튜브 · `V161` ~ `V165` 공단 영상)의 라벨로 대체 편성되어 빈 화면이 나지 않는다.
4. **시드 데이터.** 데모 가족 「데모네」(데모 엄마 PARENT·FULL, 데모 첫째 CHILD·측정 1회 있음, 데모 아빠 PARENT 미연결·초대코드 `K7M2QT`),
   데모 첫째 · 엄마의 운동할 수 있는 시간, 국민체력100 또래 분포 · 등급 기준표(AI 가 실제 공공데이터로 만든 표), AI 운동 영상 48편 · 구간 695개(`V132`, 운영에도 들어간다), 공단 「국민체력100 동영상 정보」 오픈API 영상 452편(한 편 = 구간 하나, `V161` ~ `V165`, 운영에도 들어간다. 「공통」 영상은 청소년 · 성인 둘 다 받는다. 어르신 영상은 싣지 않고 65세 이상은 성인 영상을 받는다), 시험용 가짜 영상 4편(`sample00002~5`).
   서버를 재시작하면 H2 인메모리라 시연 중 만든 데이터는 사라지고 시드만 남는다.
5. **서버 시계를 앞으로 옮길 수 있다.** 2주 여정 · 월말 리그 · 30일 뒤 다시 재기 알림을 기다리지 않고 본다.
   `POST /api/v1/dev/clock {"by":"P1D"}`(하루 뒤) 또는 `{"to":"2026-10-01T07:31:00+09:00"}`. 옮기는 동안 건너뛴 정시 작업(07:30 오늘의 운동 알림 ·
   09:00 다시 재기 알림 · 04:00 토큰 정리 · 매월 1일 00:10 리그 정산)을 원래 시각 차례대로 돌린다. 뒤로는 못 가고, 처음으로 돌아가려면 서버를 다시 띄운다.
   `GET /api/v1/dev/clock` 으로 지금 서버 시각을 본다. 브라우저 시계는 따로라, 화면으로 볼 때는 브라우저 날짜도 같은 날로 맞춘다(Playwright `page.clock`).

자주 쓰는 첫 호출:
```bash
FAMILY=00000000-0000-4000-8000-000000000010
CHILD=00000000-0000-4000-8000-000000000012   # 데모 첫째
AS='X-Dev-User-Id: 00000000-0000-4000-8000-000000000001'   # 데모 부모로 (브라우저는 로그인 화면의 개발용 계정)
curl -H "$AS" localhost:8080/api/v1/me                                   # nextStep HOME · 내 프로필 · selfProfileId
curl -H "$AS" localhost:8080/api/v1/families/$FAMILY/fitness-map         # 홈 화면 한 번에
curl -H "$AS" localhost:8080/api/v1/families/$FAMILY/profiles            # 구성원
curl -H "$AS" -X POST localhost:8080/api/v1/families/$FAMILY/coach/runs -H 'Content-Type: application/json' \
     -d "{\"profileId\":\"$CHILD\",\"date\":\"$(date +%F)\",\"minutes\":15}"   # 데모 첫째의 오늘 편성(202)
curl -H "$AS" "localhost:8080/api/v1/families/$FAMILY/coach/runs/latest?profileId=$CHILD"  # 그 편성의 결과(칸 포함)
curl -H "$AS" localhost:8080/api/v1/profiles/$CHILD/progress             # 레벨 · 경험치 · 업적
curl -H "$AS" "localhost:8080/api/v1/notifications?profileId=$CHILD"     # 데모 첫째의 알림함(보호자가 계정 없는 아이 대신)
```
실패는 항상 `{"error": {"code": "...", "message": "..."}}`. `code` 로 분기하고 `message` 는 화면에 그대로 띄우지 않는다.

지금 FE 가 부르는 이름 `rest-days` · `/sessions/{seq}/done` · `/clips` 는 계약 이름(`rest-cards` · `/complete` · `/exercises`)의 **전환기 별칭**이라
같은 핸들러가 답한다(Swagger 에는 deprecated). FE 가 계약 이름으로 옮기면 걷는다 — 목록은 [api-contract.md 「전환기 별칭」](../docs/api-contract.md).

## 모듈

모듈 = 패키지 = ERD 묶음. 각 모듈은 `domain` · `application` · `adapter` 계층을 두고, 밖으로는 `api` 패키지만 연다
([docs/architecture.md](../docs/architecture.md)). 주소 앞에는 `/api/v1` 이 붙는다.

| 모듈 | 범위 | 주소 |
|---|---|---|
| `identity` | 계정 · 가족 · 프로필 · 동의 · 초대 · 응원 · 운동할 수 있는 시간 | `auth/google` · `auth/refresh` · `auth/logout` · `auth/dev-login` · `auth/review-login` · `me` · `families` · `families/{id}/profiles` · `profiles/{id}` · `profiles/{id}/support-mode` · `profiles/{id}/consent` · `profiles/{id}/invite` · `invites/{code}` · `profiles/claim` · `profiles/{id}/availability` · `families/{id}/cheers` |
| `fitness` | 측정 항목 · 측정 등록(키 · 몸무게 · 체지방률 · 허리둘레 · AI 와 같은 백분위 굳힘) · 인증 등급(한 사람에게 하나, 읽을 때 셈) · 결과 · 이력 · 가족 체력 지도 | `fitness/items` · `profiles/{id}/fitness-tests` · `profiles/{id}/fitness-tests/latest` · `families/{id}/fitness-map` |
| `activity` | 일별 활동(초 단위) · 쉬는 날 카드 | `families/{id}/rest-cards` · `families/{id}/rest-cards/{restDate}` |
| `progress` | 경험치 원장 · 레벨 · 업적 · 이어서 한 날 | `profiles/{id}/progress` |
| `coaching` | 하루 편성(승인 게이트) · 미션과 칸 · 칸 끝 · 운동 느낌 · 캘린더 · 운동 구간 · 영상 · 대화 · 주간 요약 | `families/{id}/coach/runs` · `families/{id}/coach/runs/latest` · `coach/runs/{id}` · `coach/runs/{id}/approve` · `coach/runs/{id}/reject` · `families/{id}/missions` · `missions/{id}` · `missions/{id}/sessions/{seq}/complete` · `missions/{id}/feedback` · `missions/{id}/participants/{profileId}/confirm` · `missions/{id}/activity/steps` · `missions/{id}/activity/timer` · `families/{id}/calendar` · `exercises` · `exercises/{id}/favorite` · `videos` · `videos/{id}/favorite` · `videos/{id}/progress` · `coach/chat` · `families/{id}/report/weekly` |
| `league` | 가족 리그(월 단위 달성률 · 순위 점수 · 다섯 티어 · 월초 정산) | `families/{id}/league` |
| `notification` | 알림함(응원 · 새 운동 · 업적 · 다시 재기) | `notifications` · `notifications/read` |

경로 46개(메서드까지 53개, 전환기 별칭은 세지 않음). 범위 밖: `GET /api/v1/facilities` (공공데이터 출처 미확정).
FE 가 부르지 않는 주소 8개: coach/chat · report/weekly · videos 셋 · activity/steps · activity/timer · participants/{profileId}/confirm. 걷을지는 결정을 기다린다.

리그 순위는 달성률이 아니라 순위 점수로 매긴다(월초 정산의 오르내림도 같다).

- 점수 = 달성률(반올림 전 값) × ln(1 + 운동한 날) ÷ ln(1 + 지난 날). 0~1 이고, 지난 날마다 다 해내면 1 이다.
- 운동한 날은 쉬는 날이 아닌 날 가운데 셀 아이 누구든 해낸 날이다.
- 지난 날은 그달 1일부터 오늘(지난달은 말일)까지 쉬는 날을 뺀 날이다. 오늘은 해냈을 때만 센다.
- 미션을 만들기 전 날 · 가입하기 전 날은 달성률에서는 빼지만 지난 날에서는 빼지 않는다 — 빼면 늦게 시작해 하루 해낸 가족이 다시 1등이 된다.

응답의 `rate` 는 그대로 두고 `score` 를 더했다. 자세한 셈은 [api-contract.md 6장](../docs/api-contract.md), 코드는 `league.domain.AchievementRate`.

## AI 서비스

`shared.ai.AiGateway` 하나로 FastAPI(`family-fitness-ai`)의 `{app.ai.base-url}/v1` 을 부른다. AI 는 `/v1` 아래 다섯 주소
(fitness/assessment · fitness/trajectory · videos/search · coach/runs · coach/messages)와 헬스 체크 `/health` 를 연다.
서버는 fitness/trajectory 를 부르지 않는다(10년 예측을 걷었다, `V153`). AI 가 서비스 테이블에 쓰는 경로는 없다.

- `app.ai.mode=stub` 이면 AI 없이 결정적 가짜 응답으로 승인 게이트·대화 흐름을 끝까지 돌릴 수 있다. prod 밖에서는 기본값이다(`APP_AI_MODE` 로 바꾼다).
- 실제 AI 에 붙이기
  1. AI 저장소 README 순서대로 가상환경 · `.env`(LLM 키) · 임베딩 모델(`make embed-model`, 처음 한 번)을 준비하고 띄운다. 임베딩은 AI 서비스 안에서 돌아 서버를 따로 띄우지 않는다.
     - 로컬 개발: `make serve`(`--reload`, `http://127.0.0.1:8000`).
     - 운영: AI README 「운영에서 띄우기」 대로 `make serve-prod AI_HOST=127.0.0.1 AI_PORT=8000`(`--reload` 없음, 모델을 미리 올림). AI 에는 인증이 없어 포트를 밖에 열지 않는다 — BE 와 같은 기계면 `127.0.0.1`, 다른 기계면 사설망 주소로 띄우고 방화벽에서 BE 만 닿게 한다.

  **배포 점검 — AI 포트**
  1. [ ] 바깥망(휴대폰 LTE 등)에서 BE 의 `APP_AI_BASE_URL` 주소로 `/health` 를 불러 닿지 않는지 확인한다(`curl -m 5 <APP_AI_BASE_URL>/health` 가 시간 초과 · 연결 거부여야 한다).
  2. `./gradlew bootRun --args='--app.ai.mode=http'` — `app.ai.base-url` 기본값이 `http://localhost:8000` 이다.
- 편성은 아이 한 명의 하루다. 서버는 AI 에 편성 대상 한 명만 보내고, AI 의 클립 단위 응답(`phase · order · duration_sec · video`)을 제안 칸으로 옮긴다.
  칸마다 분은 서버가 나눈다(준비 · 정리 1분씩, 남은 분은 본운동 칸에). `recent_video_ids` 에는 대상이 편성 날 앞 14일 동안 미션으로 받은 영상 id 를 최근 것부터 최대 60개 싣는다 — AI 와 대체 편성 모두 이 영상들을 뒤로 미뤄 날마다 같은 영상이 나오지 않게 한다. 이 칸을 모르는 AI 는 버린다. `focus_factor`(보호자가 키워 주고 싶은 역량)와 `with_companion` 은 AI develop 이 아직 몰라 받아서 버린다. 대체 편성 · 스텁은 지금도 쓴다. AI 로컬 브랜치 `feature/AI-kspo-video-api`(`b070698`, 아직 병합 전)가 두 칸을 받아 `focus_factor` 를 대상 요인으로 쓰고 `with_companion` 은 LLM 문구에만 쓴다 — 이 브랜치가 병합 · 배포된 뒤부터 AI 편성에 반영된다.
- AI 가 연결 실패 · 시간 초과 · 5xx 이거나 실행 단위로 실패하면(failed · 폴링 만료 · 404) 대체 편성으로 넘어간다. 시작 호출이 409(그 프로필에 AI 실행이 아직 돎)면 3초씩 쉬며 두 번 더 부르고, 그래도 409 면 대체 편성이다. 실패 까닭은 응답의 `failureCode` 다.
  요청 · 응답 모양과 결과 처리 표는 [docs/api-contract.md](../docs/api-contract.md) 8장.

## 데이터 스크립트 (`scripts/`)

Python 3 만 있으면 된다. Windows 콘솔(cp949)에서 결과를 파일로 받을 때는 `PYTHONIOENCODING=utf-8` 을 앞에 붙인다(`ddl_to_dbml.py` 는 스스로 UTF-8 로 쓴다).

| 스크립트 | 하는 일 | 실행 |
|---|---|---|
| `value_quantiles_to_sql.py` | AI 또래 분포 표(`value_quantiles.csv`, (연령대 · 성별 · 나이 · 항목)마다 표본 수와 백분위 값 101칸) → `fitness_value_quantiles` 마이그레이션. 첫 적재는 `--create-table`(`V156`), 다음 판은 새 V 파일(표를 통째로 바꾼다). AI 입력 파일이 커밋돼 있지 않으면 멈춘다 | backend 폴더에서 `python3 scripts/value_quantiles_to_sql.py ../../family-fitness-ai > src/main/resources/db/migration/V<다음 번호>__fitness_value_quantiles_<AI 커밋>.sql` |
| `grade_thresholds_to_sql.py` | AI 등급 기준표(`grade_thresholds.csv`, 국민체력100 공식 항목별 기준) → `fitness_grade_thresholds` 마이그레이션. 첫 적재는 `--create-table`(`V154`), 다음 판은 새 V 파일(표를 통째로 바꾼다). AI 입력 파일이 커밋돼 있지 않으면 멈춘다 | backend 폴더에서 `python3 scripts/grade_thresholds_to_sql.py ../../family-fitness-ai > src/main/resources/db/migration/V<다음 번호>__fitness_grade_thresholds_<AI 커밋>.sql` |
| `grade_distribution_to_sql.py` | AI 또래 등급 비율 표(`grade_distribution.csv`, 국민체력100 인증 결과를 (연령대 · 성별 · 나이 · 등급)마다 센 것) → `fitness_grade_distribution` 마이그레이션. 첫 적재는 `--create-table`(`V159`), 다음 판은 새 V 파일. AI 입력 파일이 커밋돼 있지 않으면 멈춘다 | backend 폴더에서 `python3 scripts/grade_distribution_to_sql.py ../../family-fitness-ai > src/main/resources/db/migration/V<다음 번호>__fitness_grade_distribution_<AI 커밋>.sql` |
| `ai_clips_to_sql.py` | AI 클립 릴리스(`video_clips.csv` · `clip_labels.csv` · `corpus_meta.csv`) → 유튜브 운동 영상 · 구간 마이그레이션. 첫 적재는 `--create-table`(`V132`), 다음 릴리스는 새 V 파일. 새 표에서 빠진 유튜브 구간만 끈다. `--ref` 를 주면 작업 트리가 아니라 그 AI 커밋에서 읽는다. AI `feature/AI-follow-ups`(`a84d392` 뒤)의 세 표는 `V132` 가 실은 `2af9002` 와 같아 새 V 파일이 없다(구간 695개 모두 같다) | backend 폴더에서 `python3 scripts/ai_clips_to_sql.py --ref <AI 커밋> ../../family-fitness-ai > src/main/resources/db/migration/V<다음 번호>__coaching_clip_release_<AI 커밋>.sql` |
| `kspo_videos_to_sql.py` | AI 공단 영상 표(`kspo_videos.csv` 한 파일, 공단 「국민체력100 동영상 정보」 오픈API 영상 한 편이 연령대 · 요인 · 단계마다 한 줄 — 「공통」 영상은 청소년 · 성인 두 줄) → 영상 · 구간 마이그레이션. 한 편 = 구간 하나(`{videoId}-0`)이고, 표의 줄은 모두 `video_exercise_labels` 에 실어 운동 찾기 · 대체 편성이 줄마다 후보로 쓴다. 영상 제목 · 첫 장면 주소 · 준비물은 표에 없어 이미 실린 값을 그대로 둔다. 작업 트리가 아니라 `--ref` 로 준 AI 커밋에서 읽고 그 커밋을 파일 머리에 적는다. 이 형식 첫 적재는 `--add-labels`(`citation_label` 칸 · `video_exercise_labels` 표, `V165` = AI `a84d392`), 다음 표는 새 V 파일. 새 표에서 빠진 공단 구간만 끈다. `--note` 로 앞 표와 달라진 점을 머리 주석에 적는다. `ai_clips_to_sql.py` 를 가져다 쓰니 같은 폴더에서 돌린다. `V161` ~ `V164`(두 파일 형식 — `kspo_video_labels.csv` 가 따로 있던 때)는 이 스크립트의 앞 버전으로 만들었다 | backend 폴더에서 `python3 scripts/kspo_videos_to_sql.py --ref <AI 커밋> ../../family-fitness-ai > src/main/resources/db/migration/V<다음 번호>__coaching_kspo_release_<AI 커밋>.sql` |
| `ai_percentile_fixture.py` | AI 코드(`stats/tables.py` peer · percentile_of)로 백분위 기대값을 뽑아 `src/test/resources/fitness/ai-percentiles.csv` 를 만든다. `AiPercentileParityTest` 가 이 줄마다 서버 계산과 대조한다. AI 표가 바뀌면 마이그레이션과 같이 다시 만든다 | backend 폴더에서 `../../family-fitness-ai/.venv/Scripts/python.exe scripts/ai_percentile_fixture.py ../../family-fitness-ai > src/test/resources/fitness/ai-percentiles.csv` (macOS · Linux 는 `.venv/bin/python`) |
| `ai_certify_fixture.py` | AI 코드(`stats/assess.py` _with_body · `stats/tables.py` certify)로 인증 등급 기대값을 뽑아 `src/test/resources/fitness/ai-certify.csv` 를 만든다. `AiCertifyParityTest` 가 이 줄마다 서버 계산과 대조한다. AI 기준표가 바뀌면 마이그레이션과 같이 다시 만든다 | backend 폴더에서 `../../family-fitness-ai/.venv/Scripts/python.exe scripts/ai_certify_fixture.py ../../family-fitness-ai > src/test/resources/fitness/ai-certify.csv` |
| `ddl_to_dbml.py` | 마이그레이션 DDL → `docs/erd.dbml` | 저장소 루트에서 `python3 backend/scripts/ddl_to_dbml.py > docs/erd.dbml` |

`ddl_to_dbml.py` 는 파일을 버전 차례로, 파일 안에서는 문장 차례로 적용한다. `create table` · `create [unique] index` ·
`alter table … add/drop/rename column · alter column … set/drop not null · add/drop constraint` 를 읽고, 뒤 마이그레이션이 걷은 FK · 제약 · 인덱스는 ERD 에서도 뺀다.
insert · update · delete · select 는 건너뛴다. 그 밖의 DDL 을 만나면 ERD 를 내지 않고 그 문장을 stderr 에 적고 1로 끝난다. 그런 마이그레이션을 더했으면 스크립트를 먼저 고친다.

## 테스트

- 도메인 단위 테스트(순수 Java) → 애플리케이션 테스트(포트 대역) → 웹 테스트(MockMvc, H2 위 실제 스택) 순서로 둔다.
- `ModularityTests` 가 모듈 일곱 개가 있는지와 Spring Modulith 경계(순환 · 내부 패키지 접근 · 선언된 의존)를 검증하고, ArchUnit 으로 모듈마다 부를 수 있는 모듈 목록(아무도 notification 을 부르지 않는다 등)을 확인한다.
- `FamilyfitnessApplicationTests` 가 H2 에 전체 마이그레이션을 적용해 기동을 확인한다. `PostgresMigrationTests` 등 Testcontainers 시험은 Docker 가 없으면 건너뛴다.
- 백분위는 또래 분포 표(`fitness_value_quantiles`, `V156`)로 낸다. AI 의 `data/release/value_quantiles.csv`(국민체력100 공공데이터 2024-07~2026-07 전수로 AI 가 만든 표)를 `scripts/value_quantiles_to_sql.py` 로 옮긴 것이고, 계산식도 AI `percentile_of` 와 같다. API 키가 필요 없다. 칸이 있는 나이: 유아기 48~83개월 · 유소년 11~12세 · 청소년 13~18세 · 성인 19~64세 · 어르신(012 · 028). 만 7~10세는 공공데이터에 측정이 없어 백분위가 `null` 이다. 예전 표 `fitness_norms`(`V2` · `V3` · `V155`)는 `V157` 로 걷었다.
- `AiPercentileParityTest` 가 AI 가 낸 백분위 기대값(`scripts/ai_percentile_fixture.py`)과 서버 계산을 줄마다 대조한다.
- 인증 등급은 국민체력100 인증서처럼 한 사람(회차)에게 하나를 매긴다. 규칙은 AI `stats/tables.py` certify 를 옮겼고, 기준표는 `fitness_grade_thresholds`(`V154`, `scripts/grade_thresholds_to_sql.py`), 같은 나이 · 성별 참가자의 등급별 비율은 `fitness_grade_distribution`(`V159`, `scripts/grade_distribution_to_sql.py`)이다. 저장하지 않고 등록 · latest 응답 때 셈한다. 기준 줄이 없는 만 7~10세 · 어르신은 `NO_CRITERIA` 다. `AiCertifyParityTest` 가 AI 가 낸 등급 기대값(`scripts/ai_certify_fixture.py`)과 서버 계산을 사람마다 대조한다.

## 마이그레이션 번호

- 순번을 쓴다. 지금 마지막은 `V165` 이고 다음은 `V166` 부터다(V1 · V2 · V3 다음이 V130 이다).
- `V148` 은 비어 있다. V149 가 이미 적용됐으므로 V148 을 새로 쓰면 안 된다(아래 `outOfOrder` 때문에 검증에 실패한다).
- Flyway `outOfOrder` 가 꺼져 있다. 번호가 낮은 파일이 나중에 머지되면 검증에 실패하니, 마이그레이션이 있는 PR 은 번호 차례대로 머지한다.
- 적용된 버전 마이그레이션은 고치지 않는다. 바꿀 것이 있으면 새 V 파일을 만든다.
- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 문법만 쓴다.
- 마이그레이션을 더하면 `ddl_to_dbml.py` 로 `docs/erd.dbml` 을 다시 만든다.
