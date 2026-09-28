# API 계약 (통합본)

출처: Notion 「API 명세서」(2026-09-08 · 09-17) · FE 저장소 `BACKEND_API.md`(2026-09-25, 이하 FE 요청서) · `family-fitness-ai/docs/인터페이스-명세.md` · FigJam 보드 F0~F4.
구현 기준 문서다. 여기 없는 것은 추정하지 말고 코드 주석에 `▲ 확정 필요` 로 남긴다.
기준 시점은 develop `e6e839e`(2026-09-28, 1차 묶음 PR #4~#11 머지 뒤)다. 살아 있는 스키마는 서버의 `/v3/api-docs` 다.

## 0. 전 API 공통

- 기준 경로 `/api/v1`. 성공은 payload 그대로(봉투 없음). 지금은 모든 성공 응답에 본문이 있다(204 없음).
- 실패는 한 형태: `{"error": {"code": "RUN_IN_PROGRESS", "message": "…"}}`. 화면은 `code` 로 가르고 `message` 는 그대로 띄우지 않는다.
  - 도메인 예외뿐 아니라 Spring MVC 표준 예외(405 · 406 · 413 · 415 등)와 `/error` 경로(필터 예외 · 방화벽 거절)도 같은 봉투다. 405 에는 `Allow` 헤더가 붙는다.
  - 오류 응답의 Content-Type 은 `Accept` 와 상관없이 `application/json` 이다.
  - 400 의 `message` 에 Java 클래스 · 메서드 이름을 싣지 않는다. 본문을 못 읽으면 「요청 본문을 읽을 수 없습니다」, 경로 · 쿼리 값의 형이 틀리면 「'이름' 값의 형식이 올바르지 않습니다」 다.
  - 상태 코드 → `code` 매핑은 `shared.web.ApiErrorHandler` 한 곳에만 있다.
- 상태 코드: 200/201 정상(거부 응답 포함) · 202 비동기 접수 · 400 필수 누락 · 형식 · 범위 · 401 토큰 없음/만료 · 403 권한 없음 · 404 없음 · 405 메서드 안 받음 · 406 · 409 상태 충돌(유니크 제약 위반 포함) · 410 만료 · 413 본문이 너무 큼 · 415 Content-Type 안 받음 · 422 도메인 규칙 위반 · 429 과다 · 500 서버 오류 · 503 외부 AI 장애 · 비동기 시간 초과.
- 거부(`refused: true`)는 오류가 아니다. HTTP 200.
- 값이 없으면 칸을 빼지 않고 `null` 로 싣는다(`spring.jackson.default-property-inclusion=always`). 모르는 값은 0 이 아니라 `null` 이다(백분위 · 등급 · 키 · 몸무게).
- 인증: `Authorization: Bearer <accessToken>`. `보호자(PARENT)만` 주소는 호출 계정이 그 가족의 PARENT 프로필을 갖지 않으면 403 `NOT_A_PARENT`. 다른 가족 리소스는 403 `NOT_SAME_FAMILY`.
  공개 경로(`/api/v1/auth/**` 등)는 `Authorization` 헤더를 읽지 않는다. 만료 토큰이 실려 와도 refresh · google · dev-login 은 401 이 나지 않는다.
- actor(로그인 계정 userId)와 대상 profileId 는 다르다. 부모가 아이 기록을 대리 입력한다. HTTP 요청의 role·familyId 값을 신뢰하지 않는다.
- 날짜 `YYYY-MM-DD`, 시각 ISO-8601. 활동 날짜(activityDate)는 KST(Asia/Seoul) 기준. 한 주는 월요일에 시작한다.
- 문구 규칙: 「부족」·「미달」·「하위」 금지. `band`/`factor` 같은 코드값을 그대로 노출하지 말고 `copy`·`disclaimer`·`notice`·`headline` 같은 표시 문구는 고쳐 쓰지 않는다. 아이 화면에서 `parent_scope` 를 읽지 않는다.
- AI 서비스로 이름·생년월일·연락처·계정 식별자를 보내지 않는다. 프로필은 `profile_ref` 로만. 측정 항목 `005`·`006`(혈압)은 입력으로 받지 않는다(400 `ITEM_NOT_ALLOWED`).
- 보호자 동의(만 14세 미만)가 없거나 거둔 프로필은 편성 대상 · 편성 참여자 · 미션 참여자 · 활동 기록(타이머 · 걸음수 · 영상 진행)에 넣지 않는다. 422 `CONSENT_REQUIRED` 다. 지난 기록은 지우지 않는다.

### 오류 코드

| `code` | 상태 | 나는 곳 |
|---|---|---|
| `BAD_REQUEST` | 400 | 필수 누락 · 형식 · 범위(Bean Validation), 본문 파싱 실패, 경로 · 쿼리 형 변환 실패, 필수 헤더 누락, multipart 오류, 도메인 불변식(칸 규칙 · 목표 분 · 날짜 순서 · 미래 날짜 · 같은 측정 항목 두 번 · size 범위) |
| `NO_ITEMS` · `ITEM_NOT_ALLOWED` · `UNKNOWN_ITEM` | 400 | 측정 등록 |
| `UNAUTHORIZED` | 401 | 토큰 없음 · 만료 |
| `INVALID_REFRESH_TOKEN` | 401 | `auth/refresh` |
| `GOOGLE_AUTH_FAILED` | 401 | `auth/google` |
| `NOT_A_PARENT` | 403 | 보호자 전용 주소를 아이 계정이 부름 |
| `NOT_SAME_FAMILY` | 403 | 다른 가족의 리소스 |
| `NOT_A_PARTICIPANT` | 403 | 미션 참여자가 아님 — confirm · activity/steps · activity/timer · videos/progress(missionId) |
| `FORBIDDEN` | 403 | 남의 프로필(support-mode), 응원 `fromProfileId` 를 대신할 수 없음, 다른 프로필의 대화, Spring Security 거절 |
| `NOT_FOUND` | 404 | 없는 경로 |
| `FAMILY_NOT_FOUND` · `PROFILE_NOT_FOUND` · `MISSION_NOT_FOUND` · `COACH_RUN_NOT_FOUND` · `VIDEO_NOT_FOUND` · `FITNESS_TEST_NOT_FOUND` · `CONVERSATION_NOT_FOUND` · `CODE_NOT_FOUND` | 404 | 각 리소스가 없음 |
| `METHOD_NOT_ALLOWED` · `NOT_ACCEPTABLE` · `CONTENT_TOO_LARGE` · `UNSUPPORTED_MEDIA_TYPE` | 405 · 406 · 413 · 415 | Spring MVC 표준 예외(RFC 9110 상태 이름) |
| `CONFLICT` | 409 | 유니크 제약 위반(동시 요청이 사전 검사를 함께 지나친 경우). NOT NULL · FK 위반은 500 |
| `ALREADY_IN_FAMILY` · `ALREADY_CLAIMED` · `ALREADY_MEMBER` | 409 | 가족 만들기 · 초대 |
| `DUPLICATE_DATE` | 409 | 같은 날짜 측정 |
| `RUN_IN_PROGRESS` | 409 | 같은 (대상, 날짜)의 편성이 RUNNING |
| `ALREADY_APPROVED` · `INVALID_STATE` | 409 | 이미 승인 / 그 밖으로 끝난 편성을 다시 결정 |
| `CODE_EXPIRED` | 410 | 초대코드 기한 지남 |
| `CONSENT_REQUIRED` | 422 | 보호자 동의 없음 · 거둠(위 공통 규칙) |
| `NOT_MEASURABLE` · `ITEM_NOT_FOR_AGE_GROUP` · `NO_FITNESS_TEST` | 422 | 측정 · 예측 |
| `NO_MEASURED_MEMBER` · `INVALID_DATE` · `NOT_FAMILY_MEMBER` | 422 | 편성 · 미션 · 응원 |
| `INVALID_METRIC` · `TARGET_NOT_REACHED` | 422 | 활동 기록 · 보호자 확인 |
| `NOT_APPLICABLE` · `SELF_CHEER` | 422 | support-mode · 응원 |
| `TOO_MANY` | 429 | 응원 분당 5회 |
| `INTERNAL_ERROR` | 500 | 처리하지 못한 예외 |
| `TEMPORARILY_UNAVAILABLE` | 503 | AI 연결 실패 · 시간 초과 · 5xx, 비동기 요청 시간 초과 |
| `AI_BAD_REQUEST` | 503 | AI 가 400 을 냄(서버가 잘못 보낸 것) — 예측 · 대화 |

FE 요청서 7장의 「화면이 가르는 코드」 중 서버가 내지 않는 것:
`CONSENT_WITHDRAWN`(만들지 않는다 — 동의를 거둬도 `CONSENT_REQUIRED`) · `ALREADY_RUN_THIS_WEEK`(없앴다) · `INVALID_SLOT` · `ALREADY_REST_DAY` · `NO_REST_CARD_LEFT` · `ALREADY_MOVED`(그 주소가 아직 없다).

### 구현 상태 (2026-09-28 · develop `e6e839e`)
- 주소 34개. Notion 명세의 `GET /facilities` 는 범위 밖(공공데이터 출처 미확정).
- 1차 묶음(PR #4~#11)에서 바뀐 것
  - 편성이 「아이 한 명의 하루」 가 됐다: 요청 몸통, (대상, 날짜) 잠금, `coach/runs/latest`.
  - 미션 칸(`sessions`) 저장 · 조회, 미션 단건 조회.
  - 측정: 등급 85/65/40, latest 의 그 회차 키 · 몸무게, 레이더 6요인(민첩성), 측정 이력.
  - `ProfileSummary.sex`, 구성원 추가 때 키 · 몸무게, 필수값 누락은 500 이 아니라 400.
  - 보호자가 계정 없는 아이 이름으로 응원을 보낸다.
  - 모든 오류가 봉투로 나간다. 참여자 아님은 403 `NOT_A_PARTICIPANT`.
  - AI 운동 영상 48편 · 구간 695개를 `V132` 로 적재했다.
  - 운영 jar 의 기본 프로필(local)을 없앴다.
- 없앤 것: 일요일 20시 자동 주간 편성(`CoachRunScheduler` · `app.coach.schedule.cron`), `ALREADY_RUN_THIS_WEEK`, 422 `NOT_PARTICIPANT`(→ 403 `NOT_A_PARTICIPANT`).
- 그대로인 것: AI 장애 때 라벨 기반 대체 편성(`LabelBasedProposalPlanner`, steps[1] 이 `partial`).
- 명세와 다르게 정한 것: 코치 제안 `participants[]` 에 편성 역할 `coachRole`(주행자·동반자·응원)을 두고 `role` 은 프로필 역할(PARENT/CHILD). 영상 목록 항목에 `badges`. 예측은 `MAINTAIN` 만.
- FE 가 부르지 않는 주소(FE 요청서 5장): `coach/chat` · `predictions` · `report/weekly` · `/videos` 셋 · `activity/timer` · `activity/steps`. 걷을지는 정하지 않았다.

### 공통 타입
| 이름 | 값 |
|---|---|
| `Role` | `PARENT` · `CHILD` — 프로필 생성 시 확정, 초대받는 쪽이 못 고침 |
| `SupportMode` | `CHEER_ONLY` · `WEEKEND` · `FULL` — PARENT만 |
| `AgeGroup` | `유아기`(만 0~6; 만 4세 미만은 measurable=false) · `유소년`(7~12) · `청소년`(13~18) · `성인`(19~64) · `어르신`(65+) |
| `Sex` | `M` · `F` |
| `FitnessFactor` | `심폐지구력` · `근력` · `근지구력` · `유연성` · `민첩성` · `순발력` · `협응력` · `평형성` — 와이어 값은 한글 라벨. 요청에서는 영문 이름(`FLEXIBILITY` 등)도 받는다 |
| `Band` | `strength`(백분위 ≥75) · `steady`(25~75) · `growth`(<25) · `null`(측정값 없음) |
| `Grade` | `1등급`(백분위 ≥85) · `2등급`(≥65) · `3등급`(≥40) · `참가`(그 외) — BE 설계안 ⑪ · FE 목과 같다 |
| `TargetMetric` | `VIDEO_DONE` · `TIMER_MINUTES` · `STEPS` — `STEPS`만 `serverVerifiable=false` |
| `ActivitySource` | `MANUAL` · `TIMER` · `VIDEO` — `TIMER`·`VIDEO`만 `serverVerified=true` |
| `VerifiedBy` | `VIDEO_PROGRESS` · `TIMER` · `SELF_REPORT` |
| `ItemCode` | 측정 항목 3자리 코드. 코드가 식별자, 이름은 표기 |
| `InviteStatus` | `NONE` · `ISSUED` · `EXPIRED` · `CLAIMED` |
| `MissionOrigin` | `COACH` · `MANUAL` |
| `MissionStatus` | `ACTIVE` · `DONE` · `EXPIRED` |
| `SessionPhase` | `WARMUP` · `MAIN` · `COOLDOWN` — 미션 칸의 단계(준비 · 본 · 정리) |
| `CoachRunStatus` | `RUNNING` → `AWAITING_APPROVAL` → `APPROVED` / `REJECTED`; `RUNNING` → `FAILED` |
| `CoachRole` | `주행자` · `동반자` · `응원` — 편성 역할(문자열) |
| `CoachPlace` | `HOME` · `OUTDOOR` — 편성 조건의 장소. `null` 이면 가리지 않는다 |
| `TriggerType` | `MANUAL`. `SCHEDULE` 은 없앤 자동 편성이 남긴 옛 행에만 있다 |
| `FitnessTestSource` | `SELF_INPUT` · `CENTER_SHEET` |
| `InputGroup` | `EASY`(집에서 되는 항목, 필수) · `EQUIPMENT`(장비·공간 필요, 선택) |

### ProfileSummary (identity 가 내보내는 유일한 공개 언어)
```
{profileId, familyId, name, role, ageGroup, sex, hasAccount, inviteStatus, supportMode|null, measurable, consentRequired, consentGiven}
```
- `sex` 는 `M` · `F`. 화면이 「엄마」「아빠」 로 부를 때 쓴다.
- `measurable` = 만 4세 이상 AND (동의 불필요 또는 동의 유효). `consentRequired` = 만 14세 미만. `consentGiven` = personal·health 둘 다 true 이고 철회되지 않음(동의 불필요 성인은 true).
- 싣는 응답: 가족 구성원 목록 · `/me` · 로그인 · 리프레시 · 가족 만들기 · 구성원 추가 · 참여 방식 변경. `fitness-map` 의 `members[]` 도 같은 칸을 싣는다.
- 다른 모듈은 `Profile` 엔티티를 받지 않는다. `profileId` 만 들고 다니고 `ProfileQuery` 로 `ProfileSummary`/`ProfileDetails` 를 조회한다. `@ManyToOne(Profile)` 은 identity 밖에서 금지.

### 측정 항목 코드 (AI 명세 「측정 항목 코드」 · `family-fitness-ai/stats/items.py`)
| 코드 | 항목 | 단위 | 방향 | 요인 |
|---|---|---|---|---|
| 009 | 윗몸말아올리기 | 회 | ↑ | 근지구력 |
| 010 | 반복점프 | 회 | ↑ | 근지구력 |
| 012 | 앉아윗몸앞으로굽히기 | cm | ↑ | 유연성 |
| 013 | 일리노이 | 초 | ↓ | 민첩성 |
| 014 | 체공시간 | 초 | ↑ | 순발력 |
| 017 | 눈-손협응력 | 초 | ↓ | 협응력 |
| 019 | 교차윗몸일으키기 | 회 | ↑ | 근지구력 |
| 020 | 왕복오래달리기 | 회 | ↑ | 심폐지구력 (유아기 10m · 유소년 15m · 청소년/성인 20m → itemLabel 연령대별) |
| 021 | 10m4회왕복달리기 | 초 | ↓ | 민첩성 |
| 022 | 제자리멀리뛰기 | cm | ↑ | 순발력 |
| 028 | 상대악력 | % | ↑ | 근력 |
| 035 / 037 | 트레드밀VO2max / 스텝검사VO2max | ml/kg/min | ↑ | 심폐지구력 |
| 040 | 반응시간 | 초 | ↓ | 민첩성 |
| 041 | 성인체공시간 | 초 | ↑ | 순발력 |
| 043 | 반복옆뛰기 | 회 | ↑ | 민첩성 |
| 050 | 5m4회왕복달리기 | 초 | ↓ | 민첩성 |
| 051 | 3x3버튼누르기 | 초 | ↓ | 협응력 |
- 입력 금지: 005 이완기혈압 · 006 수축기혈압 → 400 `ITEM_NOT_ALLOWED`. 신체조성(003·004·018·042)은 점수화하지 않고 받지도 않는다(400 `UNKNOWN_ITEM`). 044 는 점수 산출 제외.
- 연령대별 항목 (AI 팀 기준표 열 매핑 `CRITERIA_COLUMNS` 기준):
  - 유아기(4~6): 020(10m 왕복오래달리기) · 028 · 009 · 012 · 050 · 022 · 051
  - 유소년(7~12): 020(15m 왕복 오래달리기) · 028 · 009 · 012 · 043 · 022
  - 청소년(13~18): 020(20m 왕복 오래달리기) · 035/037 · 028 · 009 · 010 · 012 · 013 · 014 · 017
  - 성인(19~64): 020(20m) · 035/037 · 028 · 019 · 012 · 021 · 040 · 022 · 041
  - 어르신(65+): ▲ 기준항목 미정 — 012 · 028 · 019 만 잠정
- `inputGroup`: EASY = 009 · 010 · 012 · 014 · 019 · 041 · 043 (장비 없이 집에서). EQUIPMENT = 028(악력계) · 020 · 022 · 050 · 021 · 013 (공간) · 035 · 037 · 040 · 017 · 051 (장비).
- `range`(프론트 검증용, 잠정): 009 0~120 · 010 0~120 · 012 -30~40 · 013 5~60 · 014 0~2 · 017 0~120 · 019 0~120 · 020 0~150 · 021 5~60 · 022 0~350 · 028 0~150 · 035/037 10~90 · 040 0~5 · 041 0~2 · 043 0~120 · 050 5~60 · 051 0~60.

### 백분위·등급 계산 (`PercentileCalculator`)
- `fitness_norms(item_code, sex, age_unit, age_from, age_to, percentile, norm_value, source_year)` 를 부팅 시 메모리 적재. 유아기 구간은 개월 단위. 데이터 출처는 국민체력100 공공데이터 2024-07~2026-07 전수(AI 팀 분위수 산출물). 만 7~10세는 측정이 없어 백분위 `null`. 측정값을 같은 (item, sex, 나이 구간) 규준의 percentile 포인트 사이에서 선형 보간, 표 밖은 끝점으로 자름. ↓ 항목은 방향 반전.
- 결과 백분위는 정수 1~99 로 잘라 저장(0·100 금지). 규준 없으면 `null`.
- 백분위는 저장 시점 값으로 굳힌다(규준 연도가 바뀌어도 과거 불변). `grade`·`band` 는 응답 때 굳힌 백분위에서 다시 셈한다(계산은 한 군데). 그래서 등급 기준이 바뀌면 지난 회차도 곧바로 새 기준으로 나간다. 저장된 `fitness_test_items.grade` 는 `V131` 이 85/65/40 으로 다시 채웠다.
- `topPercentText` = `상위 ${100 - percentile}%` (백분위 24 → "상위 76%").

### 고정 문구 (`shared.domain.Copy`)
- 측정 disclaimer: `국민체력100 측정 데이터를 바탕으로 한 참고 정보입니다. 질병의 진단·치료를 위한 것이 아니며, 건강에 관한 판단은 전문가와 상담하세요.`
- 예측 notice: `집단 분포를 바탕으로 한 참고 범위입니다. 개인의 변화를 나타내지 않습니다.`
- band 문구: strength 「잘하고 있는 영역」 · steady 「꾸준히 하고 있는 영역」 · growth 「지금 키우기 좋은 영역」.

---

## 1. 인증·계정·가족 (identity)

### POST /api/v1/auth/google — 토큰 없이
요청 `{authorizationCode●, redirectUri●, claimCode?}`. 구글 인가코드 교환 → id_token 검증 → (provider=GOOGLE, providerUserId=sub) find-or-create. provider 는 google 하나로 고정, 계정 병합 경로 없음.
응답 200 `{accessToken, refreshToken, userId, nextStep, profiles: ProfileSummary[]}`.
`nextStep`: 프로필 0개 + 코드 없음 `CREATE_FAMILY` · 프로필 0개 + 코드 있음 `CLAIM` · 프로필 있음 `HOME`.
오류: 401 `GOOGLE_AUTH_FAILED`.
**구현 추가(명세 외, 프론트 연동 필수):**
- `POST /api/v1/auth/refresh {refreshToken}` → 같은 응답 모양. 401 `INVALID_REFRESH_TOKEN`.
- `GET /api/v1/me` → `{userId, nextStep, profiles}`.
- `POST /api/v1/auth/dev-login {providerUserId●, email?, claimCode?}` — `app.auth.dev-login.enabled=true`(local/compose/test)일 때만 빈 등록. 구글 없이 같은 응답.
  시드 데모 계정 `demo-parent`(가족 데모네 · 프로필 3개, nextStep HOME) · `demo-parent-2`(프로필 없음, 초대코드 `K7M2QT` 로 claim 가능).
  local 에서 `X-Dev-User-Id` 헤더 값이 UUID 가 아니면 400.

### POST /api/v1/families — 로그인
요청 `{familyName●(1~20자), owner●: {name●(1~20), birthDate●(미래 불가), sex●}}`. `role` 없음 — 만든 사람은 항상 PARENT·owner.
응답 201 `{familyId, familyName, ownerProfile: ProfileSummary}`.
오류: 409 `ALREADY_IN_FAMILY`(이 계정에 이미 프로필이 붙어 있다) · 400(`owner` · `owner.birthDate` · `owner.sex` 누락 포함).
불변식: 가족에 PARENT 최소 1명; 두 INSERT 는 한 트랜잭션.

### POST /api/v1/families/{familyId}/profiles — PARENT만
요청 `{name●(1~20), birthDate●(미래 불가), sex●, role●, heightCm?(30~230), weightKg?(5~250), guardianConsent?: {personalData●, healthData●}}`.
- 만 14세 미만이면 guardianConsent 필수이고 둘 다 true 여야 저장. 서버가 동의를 자동으로 찍지 않는다; `consent_*_at`·`consent_by` 는 서버가 채움. 판정은 `GuardianConsent` 값 객체.
- `heightCm` · `weightKg` 는 가입 때 적은 값으로 프로필에 저장하고 응답에는 싣지 않는다. 범위는 측정 등록과 같다. 안 적었으면 `null` 을 보내거나 칸을 뺀다(`0` 은 범위 밖이라 400).
응답 201 ProfileSummary(`profileId, hasAccount:false, ageGroup, measurable` 포함).
오류: 422 `CONSENT_REQUIRED` · 403 `NOT_A_PARENT` · 400(`birthDate` · `sex` · `role` 누락 포함).
불변식: 역할은 생성 시 확정; 만 4세 미만도 프로필은 생성(측정만 불가).

### GET /api/v1/families/{familyId}/profiles — 로그인(가족 구성원)
응답 200 `{familyId, familyName, profiles: ProfileSummary[]}`. 다른 가족이면 403 `NOT_SAME_FAMILY`.

### POST /api/v1/profiles/{profileId}/invite — PARENT만
본문 없음. 응답 201 `{claimCode(6자리, 0/O·1/I 제외 대문자+숫자), expiresAt(+7일), shareUrl("{app.frontend-base-url}/claim?code=XXXXXX")}`. local 의 `app.frontend-base-url` 기본값은 FE 개발 서버 `http://localhost:3000` 이다.
오류: 409 `ALREADY_CLAIMED`(이미 계정이 붙은 프로필) · 403 `NOT_A_PARENT`.
`ClaimCode` 값 객체 = (code, expiresAt). 재발급 시 이전 코드 즉시 무효.

### POST /api/v1/profiles/claim — 로그인
요청 `{claimCode●}`(대소문자 무시). 응답 200 `{profileId, familyId, role, nextStep}` — PARENT면 `SUPPORT_MODE`, CHILD면 `HOME`.
오류: 410 `CODE_EXPIRED` · 409 `ALREADY_CLAIMED`(다른 계정이 먼저) · 409 `ALREADY_MEMBER`(내가 이미 이 가족 구성원) · 404 `CODE_NOT_FOUND`.
동시성: `UPDATE profiles SET user_id=? ... WHERE id=? AND user_id IS NULL` 조건부 UPDATE 한 문장, 영향 0행 → `ALREADY_CLAIMED`.

### PATCH /api/v1/profiles/{profileId}/support-mode — PARENT만
요청 `{supportMode●}`. 응답 200 ProfileSummary.
오류: 400(`{}` · `{"supportMode": null}`) · 422 `NOT_APPLICABLE`(CHILD 프로필) · 403 `FORBIDDEN`(남의 프로필 — 본인 프로필(user_id=내 계정)만).

### PATCH /api/v1/profiles/{profileId}/consent — PARENT만 (보드 외 제안 항목)
요청 `{personalData●, healthData●}`. 응답 200 `{consentGiven, consentAt|null, consentBy|null, measurable}`.
철회(둘 중 하나라도 false) 효과: measurable=false. 이후 측정 등록 · 예측 · 편성 · 승인 · 미션 만들기 · 활동 기록이 422 `CONSENT_REQUIRED`(0장 공통 규칙). 과거 기록은 지우지 않는다(▲ 법적 규칙 미결).

### POST /api/v1/families/{familyId}/cheers — 로그인(가족 구성원)
요청 `{fromProfileId●, toProfileId●(같은 가족, 자기 자신 불가), message?(≤100자), emoji?(≤20자), missionId?}` — message/emoji 중 최소 하나.
`fromProfileId` 는 다음 둘 중 하나여야 한다(`Family.canActAs`).
- (가) 호출한 계정에 붙은 프로필
- (나) 호출한 계정이 이 가족의 보호자이고, `fromProfileId` 가 이 가족의 **계정 없는** 아이 프로필 — 부모 기기를 아이가 빌려 「엄마 · 아빠한테 알리기」 · 「고마워요」 를 보낸다
그 밖(계정 있는 아이, 아직 계정 없는 부모 자리)은 403 `FORBIDDEN`.
응답 201 `{cheerId, fromProfileId, toProfileId, message, emoji, missionId, createdAt}`.
판정 차례: 400(몸통, `fromProfileId` · `toProfileId` 누락 포함) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `FORBIDDEN` → 422 `SELF_CHEER` → 422 `NOT_FAMILY_MEMBER` → 429 `TOO_MANY`((보낸 프로필, 받는 프로필) 쌍 기준 분당 5회 초과).
FE 요청서의 `stickerId` · `kind` · `replyToCheerId` 와 받은 칭찬 조회(`GET …/cheers`)는 아직 없다. 지금 FE 는 스티커 id 를 `emoji` 칸에 싣는다.
Cheer 는 별도 애그리게잇. JPA 엔티티 그대로 써도 됨.

---

## 2. 측정·예측 (fitness)

### GET /api/v1/fitness/items?ageGroup=&sex= — 로그인
응답 200 `{ageGroup, items: [{itemCode, itemName, itemLabel, unit, factor, higherIsBetter, inputGroup, optional, equipment|null, range:{min,max}}]}`. `sex` 는 현재 항목을 바꾸지 않는다(양쪽 공통).

### POST /api/v1/profiles/{profileId}/fitness-tests — 로그인(같은 가족)
요청 `{testedOn●(미래 불가), source●, heightCm?(30~230), weightKg?(5~250), items●[{itemCode●, value●}] (≥1)}`.
응답 201 `{fitnessTestId, testedOn, items:[{itemCode, itemLabel, unit, value, percentile|null, grade|null, band|null, topPercentText|null}], weakest|null, strongest|null, disclaimer}`.
오류: 400 `NO_ITEMS` · 422 `NOT_MEASURABLE`(만 4세 미만) · 422 `CONSENT_REQUIRED` · 409 `DUPLICATE_DATE` · 400 `ITEM_NOT_ALLOWED`(005/006) · 400 `UNKNOWN_ITEM` · 422 `ITEM_NOT_FOR_AGE_GROUP`(연령대 항목 아님) · 400(같은 항목 두 번 · 미래 날짜).
불변식: 항목 0개면 저장 안 함; 백분위 저장 시점에 굳음; 한 프로필 같은 날짜 측정은 하나; `FitnessTest` 통째로 저장. age_at_test = testedOn 기준 만 나이.

### GET /api/v1/profiles/{profileId}/fitness-tests?size= — 로그인(같은 가족)
측정 이력. 응답 200 `{tests:[{fitnessTestId, testedOn, overallPercentile|null, heightCm|null, weightKg|null}]}` — testedOn 이 늦은 회차부터. 이력이 없으면 빈 목록.
`size` 기본 20, 1~100 밖이면 400. `overallPercentile` 은 `fitness-map` 의 것과 같은 셈(항목 백분위 평균, 규준 붙은 항목이 없으면 null). 키 · 몸무게는 그 회차에 같이 적은 값이다.
오류: 403 `NOT_SAME_FAMILY` · 404 `PROFILE_NOT_FOUND`.

### GET /api/v1/profiles/{profileId}/fitness-tests/latest — 로그인(같은 가족)
응답 200 `{fitnessTestId, testedOn, heightCm|null, weightKg|null, radar, items, weakest|null, strongest|null, coachDirection, disclaimer}`.
이력이 없어도 **404 가 아니라 200** 이다: `fitnessTestId · testedOn · heightCm · weightKg` 는 null, `radar` 6요인 percentile null, `items:[]`, `coachDirection:"GROWTH"`.
- `heightCm` · `weightKg`: 그 회차에 같이 적은 값만. 없으면 null 이고, 프로필(가입 때) 값으로 채우지 않는다.
- `radar`: 근력 · 근지구력 · 유연성 · 심폐지구력 · 순발력 · 민첩성 차례의 `{factor, percentile|null}` 6개(요인에 항목 여럿이면 평균). 민첩성은 맨 뒤에 붙었고 앞 다섯 자리는 예전 순서다. 협응력 · 평형성은 싣지 않는다.
- `items[]`: `{itemCode, itemLabel, unit, value, percentile, grade, band, topPercentText}`. `weakest/strongest`: `{factor, itemCode, percentile}`. `coachDirection`: weakest 백분위 > 75 → `STRENGTHEN`, 아니면 `GROWTH`.
- 「가장 최근」 은 testedOn 이 가장 늦은 회차다. 지난 날짜로 측정을 적으면 방금 적은 회차가 아니라 더 늦은 회차가 나온다.

### GET /api/v1/families/{familyId}/fitness-map — 로그인(가족 구성원) (명세 외 추가 · 피그잼 F0 `/home` 가족 체력 지도 ★메인)
홈 화면 한 번의 조회. 응답 200 `{familyId, familyName, members:[{profileId, name, role, ageGroup, sex, hasAccount, supportMode, measurable, consentRequired, consentGiven, headline|null, latest|null:{fitnessTestId, testedOn, overallPercentile|null(항목 백분위 평균), weakest, strongest, coachDirection}}], disclaimer}`.
- `headline` 예: 「유소년 상위 49%」. 연령대는 **측정 당시** 연령대다. 그래서 오늘 연령대인 `ageGroup` 과 다를 수 있다(11세에 재고 지금 청소년이면 「유소년 …」).
- `latest=null` 이면 "첫 측정을 등록하면 지도가 그려져요", `measurable=false` 면 측정 버튼을 띄우지 않는다. 구성원 사이 순위·비교는 내보내지 않는다.

### POST /api/v1/profiles/{profileId}/predictions — 로그인(같은 가족) · FE 가 부르지 않음
요청 `{fitnessTestId?(생략=최신), horizonYears?(1~10, 기본 10), itemCode?(기본 028)}`.
`AiGateway.trajectory` 호출 → 결과 그대로 저장. 응답 201 `{predictionId, modelVersion, basis:"cross_sectional_group_distribution", points:[{scenario:"MAINTAIN", itemCode, yearsFromNow, p10, p50, p90}], notice}`.
`IMPROVE` 시나리오는 AI 가 내지 않는다(횡단면 자료) → MAINTAIN 만 저장. modelVersion 은 AI 응답에 없으므로 `"ai-trajectory-v1"` 고정(▲ 확정 필요).
오류: 422 `CONSENT_REQUIRED` · 422 `NO_FITNESS_TEST` · 404 `FITNESS_TEST_NOT_FOUND` · 503 `TEMPORARILY_UNAVAILABLE` · 503 `AI_BAD_REQUEST`.

---

## 3. 활동 (activity)
`activity_daily(profile_id, activity_date, source, steps, active_minutes)` unique(profile_id, activity_date, source). 공개 API(`activity.api`): `ActivityRecorder`(steps 덮어쓰기 MANUAL / 분 누적 TIMER·VIDEO), `ActivityQuery`(기간 합계). 웹 엔드포인트는 coaching 모듈의 미션 · 영상 경로에 있다.
보호자 동의가 없거나 거둔 프로필의 활동은 기록하지 않는다(422 `CONSENT_REQUIRED`). 판정은 coaching 이 기록 전에 한다.

---

## 4. 코치·미션·영상 (coaching)

### POST /api/v1/families/{familyId}/coach/runs — PARENT만
한 번의 편성 = **아이 한 명의 하루**(FE 요청서 1장 ②). triggerType 은 `MANUAL`. 자동 편성은 없다.
요청 `{profileId●, date●, minutes?(5~60), minutesPerSession?(5~60), quiet?, place?, focusFactor?, withParent?}`
- `minutes` 가 없을 때만 `minutesPerSession` 을 쓴다(옛 서버용으로 FE 가 같이 보낸다). 둘 다 없으면 400 — 기본 분을 서버가 고르지 않는다.
- `quiet` · `withParent` 가 없으면 false. `place` 는 `HOME` · `OUTDOOR` · 없음(장소를 가리지 않음). `focusFactor` 는 요인 이름(한글 또는 영문) 또는 null — null 이면 코치가 가장 낮은 요인을 고른다.
- 옛 칸 `weekStart` · `daysPerWeek` 는 받지 않는다(모르는 칸은 무시).
응답 202 `{coachRunId, status:"RUNNING", pollAfterMs:1500}`.
판정 차례: 400(몸통) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 422 `INVALID_DATE`(오늘 KST 보다 앞선 날짜) → 422 `NOT_FAMILY_MEMBER`(대상이 이 가족이 아님) → 422 `CONSENT_REQUIRED`(대상의 동의 없음) → 422 `NO_MEASURED_MEMBER`(대상이 측정 대상(만 4세 이상)인데 측정 기록이 없음. 만 4세 미만은 측정 없이 진행) → 409 `RUN_IN_PROGRESS`.
잠금
- (대상, 날짜)에 RUNNING 이 있을 때만 409 `RUN_IN_PROGRESS`. `coach_runs.lock_key`(RUNNING 동안만 `profileId|date`) 유니크 인덱스라 동시에 들어온 두 요청도 하나만 통과한다.
- 새 실행이 들어가면 같은 (대상, 날짜)의 `AWAITING_APPROVAL` 은 `REJECTED`(사유 「새 제안으로 바뀌었어요」)가 된다. `APPROVED` 뒤의 추가 편성(「AI 코치에게 더 받기」)은 막지 않는다.
- 만든 지 223초가 넘은 RUNNING 은 끝내지 못한 실행으로 보고 FAILED 로 바꿔 잠금을 푼다. 223초 = 연결 1s + 읽기 2s + 40회 × (1.5s + 연결 1s + 읽기 3s). 기동 때 한 번, 그 뒤 223초마다 돈다(`StaleCoachRunSweeper`). 정리된 실행은 AI 결과가 늦게 와도 되살아나지 않는다.
비동기(커밋 후 @Async): `AiGateway.startCoachRun` → `getCoachRun` 1.5s 간격 최대 40회 폴링 → `succeeded` 면 제안 저장 후 `AWAITING_APPROVAL`. 결과 처리 규칙은 5장.
참여자: 편성 대상(`coachRole` 주행자)이고, `withParent` 면 편성을 요청한 보호자가 PARENT · `동반자`로 붙는다. AI 가 넣은 응원 부모 · 다른 구성원은 참여자에서 뺀다. 대체 편성 · 스텁도 하루짜리 미션 1건을 낸다.
supportMode 로 역할을 정하던 규칙(`CoachRoles.of`: CHILD → 주행자, WEEKEND·FULL → 동반자, CHEER_ONLY·null → 응원)은 가족 전체를 짜던 때의 것이라 지금 편성 흐름에서는 쓰지 않는다.

### GET /api/v1/families/{familyId}/coach/runs/latest?profileId= — 로그인(가족 구성원)
가장 최근 실행(상태와 상관없이). `profileId` 가 있으면 그 프로필을 대상으로 짠 것, 없으면 가족 전체에서. 아이마다 `?profileId=` 를 붙이면 형제의 제안이 서로 가리지 않는다.
응답 200 `CoachRunView`(아래와 같은 모양). 없으면 404 `COACH_RUN_NOT_FOUND`.

### GET /api/v1/coach/runs/{runId} — 로그인(가족 구성원)
응답 200 `{coachRunId, familyId, status, weekStart, profileId|null, date|null, summary|null, steps:[{seq,name,status,summary}], proposals|null, canApprove, missionCount, rejectedReason|null}`.
- `profileId` · `date`: 누구의 어느 날을 짠 실행인지. 없앤 주간 편성이 남긴 옛 행은 둘 다 null. `weekStart` 는 그 날짜가 든 주의 월요일이다.
- `canApprove` = `AWAITING_APPROVAL` 이고 호출자가 보호자.
- `steps` 는 실행이 끝날 때 한 번에 저장된다(폴링 중에는 비어 있다).
`proposals[]`: `{position, title, rationale|null, targetMetric, targetValue, startDate, endDate, participants:[{profileId, role, coachRole}], video|null:{videoId, title|null, url, startSec|null, badges[]}, citations:[{index, label, chunkId, url|null}]}`.
- `video`: 영상은 videoId 만 저장한다(`V134` 가 `exercise_videos` FK 를 걷었다). `exercise_videos` 에 있는 영상이면 제목 · 배지를 붙이고, 없으면 `title:null` · 유튜브 주소 · `badges:[]`.
- `badges`: noise QUIET → 「조용함」, space SMALL_ROOM → 「좁은 공간 OK」, equipment null → 「준비물 없음」.
- 제안에는 아직 칸(`sessions[]`)이 없다. 그래서 코치 미션의 `sessions` 는 `[]` 다.
도메인: 승인 전 `proposalsForMissionCreation()` 은 `CoachApprovalRequiredException`.

### POST /api/v1/coach/runs/{runId}/approve — PARENT만 ★
본문 없음. 응답 200 `{coachRunId, status:"APPROVED", approvedBy(프로필 id), approvedAt, createdMissions:[{missionId, title, origin:"COACH"}]}`.
오류: 404 `COACH_RUN_NOT_FOUND` · 403 `NOT_A_PARENT` · 403 `NOT_SAME_FAMILY` · 422 `CONSENT_REQUIRED`(복사할 참여자 중 동의 없는 사람) · 409 `ALREADY_APPROVED` · 409 `INVALID_STATE`.
한 트랜잭션: 도메인 승인 → 참여자 동의 확인 → run 상태 전이(조건부 UPDATE, 영향 0행→409) → missions INSERT(proposal 복사만) → mission_participants INSERT. 참여자가 빈 제안 항목은 미션으로 만들지 않는다. 미션이 만들어지는 유일한 지점(+ 직접 만들기).
도메인 예외(기존 CoachRunTest): `ParentRoleRequiredException`(NOT_A_PARENT), `CoachFamilyAccessDeniedException`(NOT_SAME_FAMILY), `CoachRunAlreadyDecidedException`(ALREADY_APPROVED 승인 후 / INVALID_STATE 그 밖), `CoachApprovalRequiredException`.

### POST /api/v1/coach/runs/{runId}/reject — PARENT만
요청 `{reason?(≤300자)}`, 본문을 빼도 된다. 응답 200 `{coachRunId, status:"REJECTED", rejectedReason, missionCount:0}`.

### POST /api/v1/families/{familyId}/missions — PARENT만
요청 `{title●(1~50), startDate●, endDate●(≥startDate), targetMetric●, targetValue●(≥1), videoId?, participantProfileIds●(1~5, 같은 가족), sessions?(≤10)}`.
`sessions[]`(칸): `{position●(1부터), phase●(SessionPhase), title●(≤120), factor?(요인 이름), minutes●(1~60), clip?:{videoId●, startSec●(≥0), endSec●, title?(≤120)}}`
- 보낸 `position` 차례 그대로 저장하고, 단계로 다시 줄 세우지 않는다. 같이 온 `completed` · `verifiedBy` 는 버린다(끝냈는지는 사람마다다).
- 칸이 있으면 `targetMetric` 은 `TIMER_MINUTES` 여야 하고 `targetValue` 는 칸 `minutes` 합과 같아야 한다. 서버가 값을 고쳐 넣지 않는다.
- 칸 규칙을 어기면 400 `BAD_REQUEST` 다. 다만 검사하는 단계가 둘이라, 어느 규칙이냐에 따라 권한 · 동의 오류보다 먼저 나기도 하고 뒤에 나기도 한다.
  - 몸통 단계(`endDate < startDate` 와 같은 단계, 권한 판정보다 먼저): 11칸 이상, position 이 1보다 작음, minutes 가 1~60 밖, `clip.videoId` 가 `[A-Za-z0-9_-]{1,32}` 가 아님, `endSec <= startSec`.
  - 미션을 만드는 단계(`VIDEO_NOT_FOUND` 뒤): 칸이 있는데 `targetMetric` 이 `TIMER_MINUTES` 가 아님(STEPS · VIDEO_DONE 미션에 칸) → `targetValue` 가 칸 `minutes` 합과 다름 → position 이 1..n 이 아님(겹침 · 빠짐). 이 차례로 검사한다.
- 칸의 `clip.videoId` 는 카탈로그로 확인하지 않고 사본으로 저장한다(404 `VIDEO_NOT_FOUND` 가 나지 않는다). 클립 표를 다시 적재해도 지난 미션의 칸은 바뀌지 않는다.
응답 201 `{missionId, origin:"MANUAL", coachRunId:null, serverVerifiable}`.
판정 차례: 400(몸통 · `endDate < startDate` · 칸의 몸통 단계 규칙) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 422 `NOT_FAMILY_MEMBER` → 422 `CONSENT_REQUIRED` → 404 `VIDEO_NOT_FOUND`(미션 단위 `videoId`) → 400(칸의 미션 단계 규칙: 목표 지표 → 목표 분 합 → position 겹침 · 빠짐).
FE 요청서의 여러 날 한 번에 만들기(`dates[]`)는 아직 없다.

### GET /api/v1/families/{familyId}/missions?scope=ALL|MINE|FAMILY&status=ACTIVE|DONE|EXPIRED — 로그인(가족 구성원)
응답 200 `{missions: MissionView[]}`.
`MissionView` = `{missionId, title, origin, coachRunId|null, targetMetric, targetValue, serverVerifiable, startDate, endDate, rationale|null, video|null:{videoId, title|null, url, durationSec|null, startSec|null}, participants:[{profileId, name, progress, completed, verifiedBy|null, needsGuardianCheck}], sessions:[{position, phase, title, factor|null, minutes, clip|null:{videoId, startSec, endSec|null, title|null}}]}`
- `sessions` 는 position 오름차순. 칸 없는 미션(코치 미션 포함)은 `[]`. 칸에 `completed` 는 없다 — 사람별 `doneSessions` 는 아직 없다.
- `clip.endSec` 가 null 이면 구간이 아니라 영상 한 편이다. AI 영상은 길이 자료가 없어 `video.durationSec` 가 null 이다.
- 정렬: startDate 내림차순, 같으면 만든 차례.
`MINE` = 내 계정의 프로필이 참여자. `FAMILY` = 참여자 2명 이상. `ACTIVE` = 오늘 ≤ endDate 이고 전원 완료 아님; `DONE` = 전원 완료; `EXPIRED` = endDate 지났고 미완료.
진행도(서버 계산, 0.0~1.0, 읽을 때 다시 계산해 바뀐 것만 저장): VIDEO_DONE = 완주(maxProgress≥0.9) 횟수/targetValue(영상 1편이면 0 또는 1). TIMER_MINUTES = 기간 내 TIMER+VIDEO 분/targetValue. STEPS = 기간 내 MANUAL steps 합/targetValue — 도달해도 보호자 확인 전 completed=false, needsGuardianCheck=true.

### GET /api/v1/missions/{missionId} — 로그인(가족 구성원)
응답 200 `MissionView`(목록 원소와 같은 모양 · 같은 권한). 없으면 404 `MISSION_NOT_FOUND`.

### POST /api/v1/missions/{missionId}/participants/{profileId}/confirm — PARENT만 (보드 외 제안 항목)
본문 없음. 응답 200 `{missionId, profileId, completed:true, verifiedBy:"SELF_REPORT", confirmedBy, verifiedAt}`.
오류: 404 `MISSION_NOT_FOUND` · 403 `NOT_A_PARENT` · 403 `NOT_A_PARTICIPANT` · 422 `TARGET_NOT_REACHED`.

### POST /api/v1/missions/{missionId}/activity/steps — 로그인(가족 구성원) · FE 가 부르지 않음
요청 `{profileId●, activityDate●(미래 불가), steps●(0~100000, 그날 총량 덮어쓰기)}`.
응답 200 `{source:"MANUAL", serverVerified:false, verifiedBy:"SELF_REPORT", missionProgress, missionCompleted, needsGuardianCheck}`.
판정 차례: 400(미래 날짜) → 404 `MISSION_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARTICIPANT` → 422 `INVALID_METRIC`(STEPS 미션 아님) → 422 `CONSENT_REQUIRED`.

### POST /api/v1/missions/{missionId}/activity/timer — 로그인(가족 구성원) · FE 가 부르지 않음
요청 `{profileId●, startedAt●, endedAt●(> startedAt), activeMinutes●(1~180)}` — `endedAt-startedAt` 분을 넘으면 그 값으로 자른다(최소 1분).
응답 200 `{activityDate(startedAt KST), source:"TIMER", serverVerified:true, totalActiveMinutes(그날 누적, 모든 출처), missionProgress, missionCompleted}`.
판정 차례: 400 → 404 `MISSION_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARTICIPANT` → 422 `INVALID_METRIC`(TIMER_MINUTES 미션 아님) → 422 `CONSENT_REQUIRED`.
칸 하나를 끝낸 기록(FE 요청서 3장 「운동 한 칸 끝」)은 아직 없다. 이 주소는 칸을 모른다.

### GET /api/v1/videos?list=ALL|FAVORITES|RECENT&profileId=&ageGroup=&factor=&cursor=&size=20 — 로그인 · FE 가 부르지 않음
응답 200 `{videos:[{videoId, title, url("https://www.youtube.com/watch?v="), thumbnailUrl("https://i.ytimg.com/vi/{id}/hqdefault.jpg"), durationSec|null, label:{ageFrom, ageTo, factors[], intensity, space, noise, model}, favorited, maxProgress|null}], nextCursor|null}`.
`FAVORITES`·`RECENT` 는 profileId 필수(400). size 1~100. `ageGroup` 안전 필터: 라벨 연령 범위와 교차하는 영상만(라벨 없는 영상은 아이 연령대에 나가지 않음). 커서 = 마지막 videoId(정렬 videoId 오름차순). `RECENT` 는 최근 시청순이고 커서를 무시한다.

### POST /api/v1/videos/{videoId}/favorite — 로그인 · FE 가 부르지 않음
요청 `{profileId●, favorited●}`. 응답 200 `{videoId, profileId, favorited, favoritedAt|null}`. 404 `VIDEO_NOT_FOUND`.

### POST /api/v1/videos/{videoId}/progress — 로그인 · FE 가 부르지 않음
요청 `{profileId●, progress●(0~1), watchedSec●, missionId?}`.
응답 200 `{maxProgress, completed, creditedMinutes, verifiedBy|null, missionProgress|null}`.
규칙: 최대 진행률만 남김; 최초로 0.9 이상 도달 시 `activity_daily` source=VIDEO 로 영상 길이(분, 올림) 1회 적립(credited_at); 이미 적립되면 0. AI 영상은 길이 자료가 없어 `creditedMinutes` 가 0 이다. missionId 가 있으면 참여자 진행도 갱신(VIDEO_DONE 미션).
오류: 404 `VIDEO_NOT_FOUND` · 422 `CONSENT_REQUIRED` · 404 `MISSION_NOT_FOUND` · 403 `NOT_A_PARTICIPANT`.

### 운동 영상 · 구간 카탈로그 (조회 주소 없음)
- `exercise_videos` 에 AI 영상 48편, 새 표 `video_exercises` 에 구간 695개(운동 651 · 운동 아님 44)를 `V132` 가 넣는다. 모든 프로필(prod 포함)에 들어간다. local · compose 시드의 가짜 영상 4편(`sample00002~5`)은 시험용이다.
- 구간 id(`clip_id`) = `{videoId}-{startSec}`. AI 가 영상을 다시 끊어도 운동 구간의 (videoId, startSec) 는 유지됐다(9/17 → 9/22 판에서 491/491).
- 단계(`phase`)는 영상 화면 표시 → 라벨 → 본운동 순으로 정한다. AI 새 판은 `backend/scripts/ai_clips_to_sql.py` 로 새 V 파일을 만들어 적재한다. 판에서 빠진 구간은 지우지 않고 `active=false`.
- 조회 주소(설계안 `GET /exercises`)와 구간 찜은 아직 없다.

### POST /api/v1/coach/chat — 로그인(같은 가족) · FE 가 부르지 않음
요청 `{profileId●, conversationId?, question●(1~500)}`. `AiGateway.ask`.
응답 200 `{conversationId, messageId, answer, citations:[{index, sourceLabel, excerpt, url}], refused, refusalReason|null}`.
USER·ASSISTANT 메시지 모두 저장(거부도 저장). 한 대화는 한 프로필의 것(다른 프로필의 conversationId → 403 `FORBIDDEN`). 없는 대화 → 404 `CONVERSATION_NOT_FOUND`. AI 장애 → 503(저장 안 함). refused=false 인데 인용 0 → 서버가 `no_citation_generated` 거부로 바꿔 저장. excerpt 는 AI 응답에 없으면 label 로 채움.

### GET /api/v1/families/{familyId}/report/weekly?weekStart= — 로그인(가족 구성원) · FE 가 부르지 않음
응답 200 `{weekStart, weekEnd, summary|null, missionStats:{total, completed}, members:[{profileId, name, activeMinutes, verifiedMinutes, completedMissions}], cheerCount}`.
그 주(월~일)에 겹치는 미션 집계. summary = 그 주 weekStart 의 run 중 가장 최근 것의 summary.

### GET /api/v1/facilities — ▲ 공공데이터 출처 확정 필요 → 이번 구현 범위 밖.

---

## 5. AI ↔ API 서버 (`shared.ai.AiGateway`)
AI 쪽 원문은 `family-fitness-ai/docs/인터페이스-명세.md` 다. 아래는 서버가 실제로 보내고 읽는 것이다.
- `{app.ai.base-url}/v1`, JSON, 인증 없음(내부망). AI 서비스는 `/v1` 아래 다섯 주소(assessment · trajectory · videos/search · coach/runs · coach/messages)와 `/health` 를 연다. 로컬에서는 AI 저장소의 `make serve`(uvicorn, 8000번)로 띄운다.
- 모드: `app.ai.mode=stub`(local · compose 기본, AI 없이 결정적 가짜 응답) · `http`(prod 기본, `APP_AI_MODE` 로 바꾼다).
- AI 오류 봉투 `{"error":{"code","message"}}` → 서버 예외: 409 → `AiRunInProgressException`(`RUN_IN_PROGRESS`) · 404 → `AiRunNotFoundException`(`RUN_NOT_FOUND`) · 400 → `AiBadRequestException`(503 `AI_BAD_REQUEST`) · 그 밖 상태 · 연결 실패 · 시간 초과 · 빈 응답 → `AiUnavailableException`(503 `TEMPORARILY_UNAVAILABLE`).
- 시간 한도 · 재시도: 연결 1s. 읽기 assessment · trajectory 3s · 2회 / videos/search 4s · 2회 / coach/messages 10s · 0회 / POST coach/runs 2s · 0회 / GET coach/runs/{id} 3s. 재시도는 `AiUnavailableException` 에만, 200ms 부터 지수 백오프.
- `POST /v1/fitness/assessment` `{profile_ref, age, age_unit, sex, height_cm?, weight_kg?, measurements}` → `{input_level, age_group, child_scope:{focus_one|null}, parent_scope:{grade|null, peer_distribution[], factors[], copy{strength,focus}}, low_sample, disclaimer}`.
- `POST /v1/fitness/trajectory` `{profile_ref, age, age_unit, sex, height_cm?, weight_kg?, measurements?, item_code?, horizon_years?}` → `{basis, item_code, item_name, unit, bands:[{age,p10,p50,p90,n}], notice, low_sample}`.
- `POST /v1/videos/search` `{age_group●, fitness_factors?, exercise_names?, k?}`(요인 · 운동명 중 최소 하나) → `{hits:[{video_id, start_sec, score, matched_exercise_names, citation:{label, chunk_id, url?}}], filtered_out:{age_group, below_threshold}}`.
- `POST /v1/coach/messages` `{profile_ref, age_group, question}` → `{answer, citations[], refused, refusal_reason|null}`.

### 편성 요청 `POST /v1/coach/runs` — 서버가 보내는 것
```
{"profile_refs": [{"ref", "role": "주행자", "age", "age_unit", "sex", "input_level", "height_cm"?, "weight_kg"?, "measurements"?}],
 "period": {"start_date": <요청 date>, "weeks": 1},
 "constraints": {"days_per_week": 1, "minutes_per_session": <minutes>, "weekly_minutes": null,
                 "quiet": <quiet>, "small_space": <place == HOME>, "no_props": true,
                 "focus_factor": <한글 요인 | null>, "with_companion": <withParent>}}
```
- `profile_refs` 는 편성 대상 한 명뿐이다. 가족 전원을 보내지 않으므로 AI 의 「1~4명」 제한과 형제 사이 409 가 생기지 않는다. 동의가 없는 프로필은 싣지 않는다.
- 키 · 몸무게 · 측정값은 대상의 가장 최근 측정 회차 값이다. `measurements` 가 비면 칸을 null 로 보낸다.
- `focus_factor` · `with_companion` 은 AI 계약에 아직 없다. AI 가 모르는 칸을 무시하므로 http 모드에서는 고른 힘이 반영되지 않는다(대체 편성 · 스텁은 반영).
- 응답 202 `{run_id, status:"running", poll_after_ms}`.

### 편성 결과 `GET /v1/coach/runs/{run_id}` — 서버가 읽는 것
```
{run_id, status: running|succeeded|failed|refused, steps:[{seq,name,status,summary}],
 proposal|null: {missions:[{kind, title, period:{start_date,end_date}, participants:[{ref,role}], duration_min, video_sec,
                            sessions:[{day_offset, phase, order, exercise_name, fitness_factor, duration_sec,
                                       video:{video_id,start_sec,end_sec}|null, evidence:[int]}],
                            copy:{child,parent}, reason}],
                 citations:[{index,label,chunk_id,url?}], notices:[]},
 refused, refusal_reason|null}
```
- 숫자 칸(`duration_min` · `video_sec` · `duration_sec` · `order`)은 비어 와도 읽는다. 9/17 앞의 옛 모양(세션마다 `duration_min`)이면 그 합을 목표 분으로 쓴다.
- `notices` 는 읽지만 응답에 싣지 않는다.
- 제안 변환(`ProposalConverter`): missions[i] → 제안 항목 position=i, title, rationale=`reason`(비면 `copy.parent`), targetMetric=`TIMER_MINUTES`, targetValue=`duration_min`(그 회 운동 시간, 최소 1. 클립 길이 합 `video_sec` 이 아니다), video=`order` 차례로 처음 영상이 있는 세션, participants=편성 대상(+ `withParent` 면 요청 보호자, 동반자), citations=evidence 가 가리키는 것(없으면 전체). 원문 proposal · steps JSON 은 coach_runs 에 그대로 저장.
- 세션(클립) 목록은 아직 제안 · 미션 칸으로 옮기지 않는다.
- 결과 처리
  - `succeeded` → 제안 저장, `AWAITING_APPROVAL`.
  - `refused` → `FAILED`(`ai_refused=true`, 거부 사유).
  - `failed` · 40회 폴링 안에 안 끝남 → `FAILED`.
  - `AiUnavailableException`(연결 실패 · 시간 초과 · 5xx) → 라벨 기반 대체 편성. 고를 요인도 인용할 근거(측정 · 고른 힘)도 없으면 `FAILED`.
  - 그 밖 예외(AI 409 · 404 · 400 포함) → `FAILED`.
- AI 는 같은 프로필이 든 실행이 돌고 있으면 409 를 낸다. 서버 잠금은 (대상, 날짜) 단위라, 같은 아이의 다른 날 편성이 동시에 돌면 뒤의 것은 AI 409 로 `FAILED` 가 된다.

## 6. FE 요청서와 맞대 본 상태 (develop `e6e839e`)
FE 화면 ↔ 주소 대응은 FE 요청서(`BACKEND_API.md`)가 원본이다. 여기에는 서버가 어디까지 했는지만 적는다.

| FE 요청서 | 요청 | 서버 |
|---|---|---|
| 1장 ① | 계정 없는 아이 이름으로 응원 | 있음 |
| 1장 ② · 2장 | 편성 몸통 · (프로필, 날짜) 잠금 | 있음 |
| 1장 ③ | `MissionView.sessions` · `CreateMissionRequest.sessions` | 있음 |
| 1장 ③ | `ProposalView.sessions` · `participants[].doneSessions` | 없음 |
| 1장 ④ | AI 9/17 클립 형식 읽기 | 있음 |
| 1장 ⑤ | 레이더 민첩성 | 있음(latest 의 `radar`) |
| 1장 ⑥ | 편성 단계를 끝날 때마다 저장 | 없음 — 끝에 한 번에 저장 |
| 1장 ⑦ · ⑧ | 칸 끝 · calendar · progress | 없음 |
| 2장 | `ProfileSummary.sex` | 있음 |
| 2장 | `photoUrl` · `/me` 의 `selfProfileId` | 없음 |
| 2장 | 구성원 추가 키 · 몸무게, latest 의 키 · 몸무게 | 있음 |
| 2장 | 응원 `stickerId` · `kind` · `replyToCheerId` | 없음 |
| 2장 | 미션 `dates[]` | 없음. `title` 은 1~50자 |
| 3장 | 측정 이력 · `coach/runs/latest` | 있음 |
| 3장 | calendar · progress · 칸 끝 · availability · 클립 · 클립 찜 · 받은 칭찬 · 알림 · 초대코드 미리 보기 · 리그 · 쉬는 날 · 사진 | 없음 |
