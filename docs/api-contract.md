# API 계약 (통합본)

출처: Notion 「API 명세서」(2026-09-08) · Notion 「AI ↔ 웹서버 인터페이스 명세」(= `family-fitness-ai/docs/03`) · FigJam 보드 F0~F4 · `family-fitness-ai/docs/dev/AI-13`.
구현 기준 문서다. 여기 없는 것은 추정하지 말고 코드 주석에 `▲ 확정 필요` 로 남긴다.

## 0. 전 API 공통

- 기준 경로 `/api/v1`. 성공은 payload 그대로(봉투 없음). 실패는 한 형태:
  `{"error": {"code": "RUN_IN_PROGRESS", "message": "실행 중인 코치 실행이 있습니다"}}` — `message`는 개발자용, 화면에 그대로 노출하지 않는다.
- 상태 코드: 200/201 정상(거부 응답 포함) · 202 비동기 접수 · 400 필수 누락/타입 불일치(`BAD_REQUEST`) · 401 토큰 없음/만료(`UNAUTHORIZED`) · 403 권한 없음 · 404 없음 · 409 상태 충돌 · 410 만료 · 422 도메인 규칙 위반 · 429 과다 · 503 외부 AI 장애(`TEMPORARILY_UNAVAILABLE`).
- 거부(`refused: true`)는 오류가 아니다. HTTP 200.
- 인증: `Authorization: Bearer <accessToken>`. `보호자(PARENT)만` 엔드포인트는 호출 계정이 해당 가족의 PARENT 프로필을 갖지 않으면 403 `NOT_A_PARENT`. 다른 가족 리소스 접근은 403 `NOT_SAME_FAMILY`.
- actor(로그인 계정 userId)와 대상 profileId 는 다르다. 부모가 아이 기록을 대리 입력한다. HTTP 요청의 role·familyId 값을 신뢰하지 않는다.
- 날짜 `YYYY-MM-DD`, 시각 ISO-8601. 활동 날짜(activityDate)는 KST(Asia/Seoul) 기준.
- 문구 규칙: 「부족」·「미달」·「하위」 금지. `band`/`factor` 같은 코드값을 그대로 노출하지 말고 `copy`·`disclaimer`·`notice` 같은 표시 문구는 고쳐 쓰지 않는다. 아이 화면에서 `parent_scope` 를 읽지 않는다.
- AI 서비스로 이름·생년월일·연락처·계정 식별자를 보내지 않는다. 프로필은 `profile_ref` 로만. 측정 항목 `005`·`006`(혈압)은 입력으로 받지 않는다(400 `ITEM_NOT_ALLOWED`).

### 구현 상태 (2026-09-09)
Notion 백엔드 엔드포인트 27개 중 `GET /facilities` 를 뺀 26개 + 추가 5개(`auth/refresh` · `me` · `auth/dev-login` · `participants/{profileId}/confirm` · `families/{familyId}/fitness-map`) 구현. 서버의 `/v3/api-docs` 가 살아 있는 스키마다.
피그잼 F3 의 「일요일 20:00 자동 실행」(SCHEDULE 트리거, `app.coach.schedule.cron`)과 「AI 장애 시 라벨 기반 편성」(`LabelBasedProposalPlanner`, steps[1] 이 `partial`)도 구현.
명세와 다르게 정한 것: 코치 제안 `participants[]` 에 편성 역할 `coachRole`(주행자·동반자·응원)을 추가하고 `role` 은 프로필 역할(PARENT/CHILD). 영상 목록 항목에 `badges` 추가. 예측은 `MAINTAIN` 만.

### 공통 타입
| 이름 | 값 |
|---|---|
| `Role` | `PARENT` · `CHILD` — 프로필 생성 시 확정, 초대받는 쪽이 못 고침 |
| `SupportMode` | `CHEER_ONLY` · `WEEKEND` · `FULL` — PARENT만 |
| `AgeGroup` | `유아기`(만 0~6; 만 4세 미만은 measurable=false) · `유소년`(7~12) · `청소년`(13~18) · `성인`(19~64) · `어르신`(65+) |
| `Sex` | `M` · `F` |
| `FitnessFactor` | `심폐지구력` · `근력` · `근지구력` · `유연성` · `민첩성` · `순발력` · `협응력` · `평형성` |
| `Band` | `strength`(백분위 ≥75) · `steady`(25~75) · `growth`(<25) · `null`(측정값 없음) |
| `TargetMetric` | `VIDEO_DONE` · `TIMER_MINUTES` · `STEPS` — `STEPS`만 `serverVerifiable=false` |
| `ActivitySource` | `MANUAL` · `TIMER` · `VIDEO` — `TIMER`·`VIDEO`만 `serverVerified=true` |
| `VerifiedBy` | `VIDEO_PROGRESS` · `TIMER` · `SELF_REPORT` |
| `ItemCode` | 측정 항목 3자리 코드. 코드가 식별자, 이름은 표기 |
| `InviteStatus` | `NONE` · `ISSUED` · `EXPIRED` · `CLAIMED` |
| `MissionOrigin` | `COACH` · `MANUAL` |
| `CoachRunStatus` | `RUNNING` → `AWAITING_APPROVAL` → `APPROVED` / `REJECTED`; `RUNNING` → `FAILED` |
| `TriggerType` | `SCHEDULE` · `MANUAL` |
| `FitnessTestSource` | `SELF_INPUT` · `CENTER_SHEET` |
| `Grade` | `1등급` · `2등급` · `3등급` · `참가` (백분위→등급 임계값 ▲ 확정 필요: 잠정 1등급≥90 · 2등급≥75 · 3등급≥50 · 그 외 참가) |
| `InputGroup` | `EASY`(집에서 되는 항목, 필수) · `EQUIPMENT`(장비·공간 필요, 선택) |

### ProfileSummary (identity 가 내보내는 유일한 공개 언어)
```
{profileId, familyId, name, role, ageGroup, hasAccount, inviteStatus, supportMode|null, measurable, consentRequired, consentGiven}
```
- `measurable` = 만 4세 이상 AND (동의 불필요 또는 동의 유효). `consentRequired` = 만 14세 미만. `consentGiven` = personal·health 둘 다 true 이고 철회되지 않음(동의 불필요 성인은 true).
- 다른 모듈은 `Profile` 엔티티를 받지 않는다. `profileId` 만 들고 다니고 `ProfileQuery` 로 `ProfileSummary`/`ProfileDetails` 를 조회한다. `@ManyToOne(Profile)` 은 identity 밖에서 금지.

### 측정 항목 코드 (AI 명세 §9 · `family-fitness-ai/stats/items.py`)
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
- `grade`·`band` 는 백분위에서 파생(계산은 한 군데). `topPercentText` = `상위 ${100 - percentile}%` (백분위 24 → "상위 76%").
- 저장 시점 값으로 굳힌다(규준 연도가 바뀌어도 과거 불변).

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
**구현 추가(명세 외, 프론트 연동 필수):**
- `POST /api/v1/auth/refresh {refreshToken}` → 같은 응답 모양. 401 `INVALID_REFRESH_TOKEN`.
- `GET /api/v1/me` → `{userId, nextStep, profiles}`.
- `POST /api/v1/auth/dev-login {providerUserId●, email?, claimCode?}` — `app.auth.dev-login.enabled=true`(local/compose/test)일 때만 빈 등록. 구글 없이 같은 응답.
  시드 데모 계정 `demo-parent`(가족 데모네 · 프로필 3개, nextStep HOME) · `demo-parent-2`(프로필 없음, 초대코드 `K7M2QT` 로 claim 가능).

### POST /api/v1/families — 로그인
요청 `{familyName●(1~20자), owner: {name●(1~20), birthDate●(미래 불가), sex●}}`. `role` 없음 — 만든 사람은 항상 PARENT·owner.
응답 201 `{familyId, familyName, ownerProfile: ProfileSummary}`.
오류: 409 `ALREADY_IN_FAMILY`(이 계정에 이미 프로필이 붙어 있다) · 400.
불변식: 가족에 PARENT 최소 1명; 두 INSERT 는 한 트랜잭션.

### POST /api/v1/families/{familyId}/profiles — PARENT만
요청 `{name●, birthDate●, sex●, role●, guardianConsent?: {personalData●, healthData●}}` — 만 14세 미만이면 guardianConsent 필수이고 둘 다 true 여야 저장. 서버가 동의를 자동으로 찍지 않는다; `consent_*_at`·`consent_by` 는 서버가 채움. 판정은 `GuardianConsent` 값 객체.
응답 201 ProfileSummary(`profileId, hasAccount:false, ageGroup, measurable` 포함).
오류: 422 `CONSENT_REQUIRED` · 403 `NOT_A_PARENT`.
불변식: 역할은 생성 시 확정; 만 4세 미만도 프로필은 생성(측정만 불가).

### GET /api/v1/families/{familyId}/profiles — 로그인(가족 구성원)
응답 200 `{familyId, familyName, profiles: ProfileSummary[]}`. 다른 가족이면 403 `NOT_SAME_FAMILY`.

### POST /api/v1/profiles/{profileId}/invite — PARENT만
본문 없음. 응답 201 `{claimCode(6자리, 0/O·1/I 제외 대문자+숫자), expiresAt(+7일), shareUrl("{app.frontend-base-url}/claim?code=XXXXXX")}`.
오류: 409 `ALREADY_CLAIMED`(이미 계정이 붙은 프로필) · 403 `NOT_A_PARENT`.
`ClaimCode` 값 객체 = (code, expiresAt). 재발급 시 이전 코드 즉시 무효.

### POST /api/v1/profiles/claim — 로그인
요청 `{claimCode●}`(대소문자 무시). 응답 200 `{profileId, familyId, role, nextStep}` — PARENT면 `SUPPORT_MODE`, CHILD면 `HOME`.
오류: 410 `CODE_EXPIRED` · 409 `ALREADY_CLAIMED`(다른 계정이 먼저) · 409 `ALREADY_MEMBER`(내가 이미 이 가족 구성원) · 404 `CODE_NOT_FOUND`.
동시성: `UPDATE profiles SET user_id=? ... WHERE id=? AND user_id IS NULL` 조건부 UPDATE 한 문장, 영향 0행 → `ALREADY_CLAIMED`.

### PATCH /api/v1/profiles/{profileId}/support-mode — PARENT만
요청 `{supportMode●}`. 응답 200 ProfileSummary.
오류: 422 `NOT_APPLICABLE`(CHILD 프로필) · 403 `FORBIDDEN`(남의 프로필 — 본인 프로필(user_id=내 계정)만).

### PATCH /api/v1/profiles/{profileId}/consent — PARENT만 (보드 외 제안 항목)
요청 `{personalData●, healthData●}`. 응답 200 `{consentGiven, consentAt|null, consentBy|null, measurable}`.
철회(둘 중 하나라도 false) 효과: 이후 측정 등록·예측 422 `CONSENT_REQUIRED`, measurable=false. 과거 기록은 지우지 않는다(▲ 미결).

### POST /api/v1/families/{familyId}/cheers — 로그인
요청 `{fromProfileId●(내 계정의 프로필), toProfileId●(같은 가족, 자기 자신 불가), message?(≤100자), emoji?, missionId?}` — message/emoji 중 최소 하나.
응답 201 `{cheerId, fromProfileId, toProfileId, message, emoji, missionId, createdAt}`.
오류: 422 `SELF_CHEER` · 422 `NOT_FAMILY_MEMBER` · 403 `FORBIDDEN`(fromProfileId 가 내 프로필이 아님) · 429 `TOO_MANY`(같은 대상에 분당 5회 초과).
Cheer 는 별도 애그리게잇. JPA 엔티티 그대로 써도 됨.

---

## 2. 측정·예측 (fitness)

### GET /api/v1/fitness/items?ageGroup=&sex= — 로그인
응답 200 `{ageGroup, items: [{itemCode, itemName, itemLabel, unit, factor, higherIsBetter, inputGroup, optional, equipment|null, range:{min,max}}]}`. `sex` 는 현재 항목을 바꾸지 않는다(양쪽 공통).

### POST /api/v1/profiles/{profileId}/fitness-tests — 로그인(같은 가족)
요청 `{testedOn●(미래 불가), source●, heightCm?(30~230), weightKg?(5~250), items●[{itemCode●, value●}] (≥1)}`.
응답 201 `{fitnessTestId, testedOn, items:[{itemCode, itemLabel, unit, value, percentile|null, grade|null, band|null, topPercentText|null}], weakest|null, strongest|null, disclaimer}`.
오류: 400 `NO_ITEMS` · 422 `NOT_MEASURABLE`(만 4세 미만) · 422 `CONSENT_REQUIRED` · 409 `DUPLICATE_DATE` · 400 `ITEM_NOT_ALLOWED`(005/006) · 400 `UNKNOWN_ITEM` · 422 `ITEM_NOT_FOR_AGE_GROUP`(연령대 항목 아님).
불변식: 항목 0개면 저장 안 함; 백분위 저장 시점에 굳음; 한 프로필 같은 날짜 측정은 하나; `FitnessTest` 통째로 저장. age_at_test = testedOn 기준 만 나이.

### GET /api/v1/profiles/{profileId}/fitness-tests/latest — 로그인(같은 가족)
응답 200 (이력 없으면 `fitnessTestId:null, testedOn:null, radar:[5요인 percentile:null], items:[], weakest:null, strongest:null, coachDirection:"GROWTH", disclaimer` — **404 아님**).
`radar`: 근력·근지구력·유연성·심폐지구력·순발력 `{factor, percentile|null}` (요인에 항목 여럿이면 평균). `items[]`: `{itemCode, itemLabel, unit, value, percentile, grade, band, topPercentText}`. `weakest/strongest`: `{factor, itemCode, percentile}`. `coachDirection`: weakest 백분위 > 75 → `STRENGTHEN`, 아니면 `GROWTH`.

### GET /api/v1/families/{familyId}/fitness-map — 로그인(가족 구성원) (명세 외 추가 · 피그잼 F0 `/home` 가족 체력 지도 ★메인)
홈 화면 한 번의 조회. 응답 200 `{familyId, familyName, members:[{profileId, name, role, ageGroup, hasAccount, supportMode, measurable, consentRequired, consentGiven, headline|null("유소년 상위 49%"), latest|null:{fitnessTestId, testedOn, overallPercentile(항목 백분위 평균), weakest, strongest, coachDirection}}], disclaimer}`.
`latest=null` 이면 "첫 측정을 등록하면 지도가 그려져요", `measurable=false` 면 측정 버튼을 띄우지 않는다. 구성원 사이 순위·비교는 내보내지 않는다.

### POST /api/v1/profiles/{profileId}/predictions — 로그인(같은 가족)
요청 `{fitnessTestId?(생략=최신), horizonYears?(1~10, 기본 10), itemCode?(기본 028)}`.
`AiGateway.trajectory` 호출 → 결과 그대로 저장. 응답 201 `{predictionId, modelVersion, basis:"cross_sectional_group_distribution", points:[{scenario:"MAINTAIN", itemCode, yearsFromNow, p10, p50, p90}], notice}`.
`IMPROVE` 시나리오는 AI 가 내지 않는다(횡단면 자료) → MAINTAIN 만 저장 (▲ AI-13 §3.7). modelVersion 은 AI 응답에 없으므로 `"ai-trajectory-v1"` 고정(▲ 확정 필요).
오류: 422 `CONSENT_REQUIRED` · 422 `NO_FITNESS_TEST` · 503 `TEMPORARILY_UNAVAILABLE`.

---

## 3. 활동 (activity)
`activity_daily(profile_id, activity_date, source, steps, active_minutes)` unique(profile_id, activity_date, source). 공개 API(`activity.api`): `ActivityRecorder`(steps 덮어쓰기 MANUAL / 분 누적 TIMER·VIDEO), `ActivityQuery`(기간 합계). 웹 엔드포인트는 coaching 모듈의 미션 경로에 있음.

---

## 4. 코치·미션·영상 (coaching)

### POST /api/v1/families/{familyId}/coach/runs — 로그인(가족 구성원)
요청 `{weekStart?(생략=이번 주 월요일), daysPerWeek?(1~7, 기본 3), minutesPerSession?(5~60, 기본 15)}`. triggerType 버튼=`MANUAL`.
응답 202 `{coachRunId, status:"RUNNING", pollAfterMs}`. 비동기(커밋 후 @Async): `AiGateway.startCoachRun` → `getCoachRun` 1.5s 간격 최대 40회 폴링 → `succeeded` 면 proposal 저장 후 `AWAITING_APPROVAL`; `refused`/`failed`/타임아웃/예외 → `FAILED`(failure_reason, ai_refused). steps JSON 저장.
편성 역할: CHILD → `주행자`; PARENT supportMode CHEER_ONLY → `응원`(편성 제외, 참여자 기록); WEEKEND·FULL → `동반자`; supportMode null → `응원`.
오류: 409 `RUN_IN_PROGRESS`(같은 가족에 RUNNING run) · 409 `ALREADY_RUN_THIS_WEEK`(같은 weekStart 에 AWAITING_APPROVAL/APPROVED run; REJECTED/FAILED 는 새 run 허용) · 422 `NO_MEASURED_MEMBER` · 403 `NOT_SAME_FAMILY`.

### GET /api/v1/coach/runs/{runId} — 로그인(가족 구성원)
응답 200 `{coachRunId, familyId, status, weekStart, summary|null, steps:[{seq,name,status,summary}], proposals|null, canApprove, missionCount, rejectedReason|null}`.
`proposals[]`: `{position, title, rationale, targetMetric, targetValue, startDate, endDate, participants:[{profileId, role, coachRole}], video|null:{videoId, title, url, startSec, badges[]}, citations:[{index, label, chunkId, url}]}`.
`badges`: noise QUIET → 「조용함」, space SMALL_ROOM → 「좁은 공간 OK」, equipment null → 「준비물 없음」.
도메인: 승인 전 `proposalsForMissionCreation()` 은 `CoachApprovalRequiredException`.

### POST /api/v1/coach/runs/{runId}/approve — PARENT만 ★
본문 없음. 응답 200 `{coachRunId, status:"APPROVED", approvedBy(프로필 id), approvedAt, createdMissions:[{missionId, title, origin:"COACH"}]}`.
오류: 403 `NOT_A_PARENT` · 403 `NOT_SAME_FAMILY` · 409 `ALREADY_APPROVED` · 409 `INVALID_STATE`.
한 트랜잭션: run 상태 전이(조건부 UPDATE, 영향 0행→409) → missions INSERT(proposal 복사만) → mission_participants INSERT. 미션이 만들어지는 유일한 지점(+ 직접 만들기).
도메인 예외(기존 CoachRunTest): `ParentRoleRequiredException`(NOT_A_PARENT), `CoachFamilyAccessDeniedException`(NOT_SAME_FAMILY), `CoachRunAlreadyDecidedException`(ALREADY_APPROVED 승인 후 / INVALID_STATE 거절 후), `CoachApprovalRequiredException`.

### POST /api/v1/coach/runs/{runId}/reject — PARENT만
요청 `{reason?(≤300자)}`. 응답 200 `{coachRunId, status:"REJECTED", rejectedReason, missionCount:0}`.

### POST /api/v1/families/{familyId}/missions — PARENT만
요청 `{title●(1~50), startDate●, endDate●(≥startDate), targetMetric●, targetValue●(>0), videoId?, participantProfileIds●(1~5, 같은 가족)}`.
응답 201 `{missionId, origin:"MANUAL", coachRunId:null, serverVerifiable}`. 오류: 403 `NOT_A_PARENT` · 422 `NOT_FAMILY_MEMBER` · 404 `VIDEO_NOT_FOUND`.

### GET /api/v1/families/{familyId}/missions?scope=ALL|MINE|FAMILY&status=ACTIVE|DONE|EXPIRED — 로그인(가족 구성원)
응답 200 `{missions:[{missionId, title, origin, coachRunId|null, targetMetric, targetValue, serverVerifiable, startDate, endDate, rationale|null, video|null:{videoId,title,url,durationSec,startSec}, participants:[{profileId, name, progress, completed, verifiedBy|null, needsGuardianCheck}]}]}`.
`MINE` = 내 계정의 프로필이 참여자. `FAMILY` = 참여자 2명 이상. `ACTIVE` = 오늘 ≤ endDate 이고 전원 완료 아님; `DONE` = 전원 완료; `EXPIRED` = endDate 지났고 미완료.
진행도(서버 계산, 0.0~1.0): VIDEO_DONE = 완주(maxProgress≥0.9) 횟수/targetValue(영상 1편이면 0 또는 1). TIMER_MINUTES = 기간 내 TIMER+VIDEO 분/targetValue. STEPS = 기간 내 MANUAL steps 합/targetValue — 도달해도 보호자 확인 전 completed=false, needsGuardianCheck=true.

### POST /api/v1/missions/{missionId}/participants/{profileId}/confirm — PARENT만 (보드 외 제안 항목)
본문 없음. 응답 200 `{missionId, profileId, completed:true, verifiedBy:"SELF_REPORT", confirmedBy, verifiedAt}`. 오류: 422 `TARGET_NOT_REACHED` · 422 `NOT_PARTICIPANT` · 403 `NOT_A_PARENT`.

### POST /api/v1/missions/{missionId}/activity/steps — 로그인(가족 구성원)
요청 `{profileId●, activityDate●(미래 불가), steps●(0~100000, 그날 총량 덮어쓰기)}`.
응답 200 `{source:"MANUAL", serverVerified:false, verifiedBy:"SELF_REPORT", missionProgress, missionCompleted, needsGuardianCheck}`.
오류: 422 `NOT_PARTICIPANT` · 422 `INVALID_METRIC`(STEPS 미션 아님) · 403 `NOT_SAME_FAMILY`.

### POST /api/v1/missions/{missionId}/activity/timer — 로그인(가족 구성원)
요청 `{profileId●, startedAt●, endedAt●(> startedAt), activeMinutes●(1~180)}` — `endedAt-startedAt` 분을 넘으면 그 값으로 자른다.
응답 200 `{activityDate(startedAt KST), source:"TIMER", serverVerified:true, totalActiveMinutes(그날 누적, 모든 출처), missionProgress, missionCompleted}`.
오류: 422 `NOT_PARTICIPANT` · 422 `INVALID_METRIC`(TIMER_MINUTES 미션 아님).

### GET /api/v1/videos?list=ALL|FAVORITES|RECENT&profileId=&ageGroup=&factor=&cursor=&size=20 — 로그인
응답 200 `{videos:[{videoId, title, url("https://www.youtube.com/watch?v="), thumbnailUrl("https://i.ytimg.com/vi/{id}/hqdefault.jpg"), durationSec, label:{ageFrom, ageTo, factors[], intensity, space, noise, model}, favorited, maxProgress|null}], nextCursor|null}`.
`FAVORITES`·`RECENT` 는 profileId 필수(400). `ageGroup` 안전 필터: 라벨 연령 범위와 교차하는 영상만(라벨 없는 영상은 아이 연령대에 나가지 않음). 커서 = 마지막 videoId(정렬 videoId 오름차순).

### POST /api/v1/videos/{videoId}/favorite — 로그인
요청 `{profileId●, favorited●}`. 응답 200 `{videoId, profileId, favorited, favoritedAt|null}`. 404 `VIDEO_NOT_FOUND`.

### POST /api/v1/videos/{videoId}/progress — 로그인
요청 `{profileId●, progress●(0~1), watchedSec●, missionId?}`.
응답 200 `{maxProgress, completed, creditedMinutes, verifiedBy|null, missionProgress|null}`.
규칙: 최대 진행률만 남김; 최초로 0.9 이상 도달 시 `activity_daily` source=VIDEO 로 영상 길이(분, 올림) 1회 적립(credited_at); 이미 적립되면 0. missionId 가 있으면 참여자 진행도 갱신(VIDEO_DONE 미션).

### POST /api/v1/coach/chat — 로그인(같은 가족)
요청 `{profileId●, conversationId?, question●(1~500)}`. `AiGateway.ask`.
응답 200 `{conversationId, messageId, answer, citations:[{index, sourceLabel, excerpt, url}], refused, refusalReason|null}`.
USER·ASSISTANT 메시지 모두 저장(거부도 저장). 한 대화는 한 프로필의 것(다른 프로필의 conversationId → 403 `FORBIDDEN`). AI 장애 → 503(저장 안 함). refused=false 인데 인용 0 → 서버가 `no_citation_generated` 거부로 바꿔 저장. excerpt 는 AI 응답에 없으면 label 로 채움.

### GET /api/v1/families/{familyId}/report/weekly?weekStart= — 로그인(가족 구성원)
응답 200 `{weekStart, weekEnd, summary|null, missionStats:{total, completed}, members:[{profileId, name, activeMinutes, verifiedMinutes, completedMissions}], cheerCount}`.
그 주(월~일)에 겹치는 미션 집계. summary = 그 주 weekStart 의 run 중 가장 최근 것의 summary.

### GET /api/v1/facilities — ▲ 공공데이터 출처 확정 필요 → 이번 구현 범위 밖.

---

## 5. AI ↔ API 서버 (`shared.ai.AiGateway`)
- `{app.ai.base-url}/v1`, JSON, 인증 없음(내부망). 오류 `{"error":{"code","message"}}`: 400 `BAD_REQUEST`/`ITEM_NOT_ALLOWED`, 404 `RUN_NOT_FOUND`, 409 `RUN_IN_PROGRESS`, 503 `TEMPORARILY_UNAVAILABLE`.
- 타임아웃/재시도: assessment·trajectory 3s·2회(지수 백오프) / videos/search 4s·2회 / coach/messages 10s·0회 / POST coach/runs 2s·0회 / GET coach/runs/{id} 3s.
- `POST /v1/fitness/assessment` `{profile_ref, age, age_unit, sex, height_cm?, weight_kg?, measurements}` → `{input_level, age_group, child_scope:{focus_one|null}, parent_scope:{grade|null, peer_distribution[], factors[], copy{strength,focus}}, low_sample, disclaimer}`.
- `POST /v1/fitness/trajectory` `{profile_ref, age, age_unit, sex, height_cm?, weight_kg?, measurements?, item_code?, horizon_years?}` → `{basis, item_code, item_name, unit, bands:[{age,p10,p50,p90,n}], notice, low_sample, child_scope:null}`.
- `POST /v1/videos/search` `{age_group●, fitness_factors?, exercise_names?, k?}` → `{hits:[{video_id, start_sec, score, matched_exercise_names, citation:{label, chunk_id, url?}}], filtered_out:{age_group, below_threshold}}`.
- `POST /v1/coach/runs` `{profile_refs:[{ref, role, age, age_unit, sex, input_level, height_cm?, weight_kg?, measurements?}], period:{start_date, weeks}, constraints:{days_per_week, minutes_per_session}}` → 202 `{run_id, status:"running", poll_after_ms}`.
- `GET /v1/coach/runs/{run_id}` → `{run_id, status: running|succeeded|failed|refused, steps:[{seq,name,status,summary}], proposal|null:{missions:[{title, period:{start_date,end_date}, participants:[{ref,role}], sessions:[{day_offset, exercise_name, fitness_factor, duration_min, video:{video_id,start_sec}|null, evidence:[int]}], copy:{child,parent}}], citations:[{index,label,chunk_id,url?}]}, refused, refusal_reason|null}`.
  - 서버 변환: proposal.missions[i] → proposal item position=i, title, rationale=copy.parent, targetMetric=`TIMER_MINUTES`, targetValue=Σduration_min, video=첫 video≠null 세션, participants ref→profileId 역매핑(`ProfileRef.indexOf`), citations = evidence 가 가리키는 run-level citations(없으면 전체). 원문 proposal·steps JSON 은 coach_runs 에 그대로 저장.
- `POST /v1/coach/messages` `{profile_ref, age_group, question}` → `{answer, citations[], refused, refusal_reason|null}`.
- **현재 AI 서비스(`family-fitness-ai`)는 `/healthz`·`/readyz` 만 있고 `/v1/*` 는 미구현(AI-4 이후)** → local 기본은 `app.ai.mode=stub`.

## 6. 사이트맵 (Figma F0) — 프론트 화면 ↔ API
`/` 스플래시(토큰 있으면 /home) · `/onboarding/login`(auth/google) · `/onboarding/family`(POST families) · `/onboarding/members`(POST/GET profiles, invite) · `/claim`(claim) · `/home`(GET profiles, GET missions, latest per profile) · `/p/:id/measure`(items, POST fitness-tests) · `/p/:id/result`(latest) · `/p/:id/future`(predictions) · `/coach/weekly`(coach runs) · `/coach/chat` · `/missions`, `/missions/:id`(activity, progress) · `/videos*` · `/family/cheer` · `/family/report` · `/family/facilities`(범위 밖) · `/settings/support-mode` · `/settings/consent`.
