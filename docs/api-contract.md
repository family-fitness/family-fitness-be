# API 계약 (통합본)

출처: Notion 「API 명세서」(2026-09-08 · 09-17) · FE 저장소 `BACKEND_API.md`(2026-09-25, 이하 FE 요청서) · `family-fitness-ai/docs/인터페이스-명세.md` · FigJam 보드 F0~F4.
구현 기준 문서다. 여기 없는 것은 추정하지 말고 코드 주석에 `▲ 확정 필요` 로 남긴다.
기준 시점은 `feature/BE-35-launch-readiness`(2026-09-29)다 — develop `56421ae`(PR #4~#34 머지 뒤)에 출시 준비 커밋을 얹은 것이고 아직 develop 에 병합하지 않았다. 살아 있는 스키마는 서버의 `/v3/api-docs` 다.
- null 이 될 수 있는 칸(jspecify `@Nullable`)은 스키마에 `type: [T, "null"]` 로 싣는다. 다른 스키마를 가리키는 칸은 `oneOf: [$ref, {type: "null"}]` 다. `@NotNull` 이 같이 붙은 요청 칸은 null 을 싣지 않는다. required 목록은 그대로다.
- 로그인 계정 인자(`CurrentUser`)는 JWT 에서 채우므로 스키마에 `user` 쿼리 파라미터로 싣지 않는다. 응원 요청의 검증용 `contentPresent` 도 싣지 않는다.

## 0. 전 API 공통

- 기준 경로 `/api/v1`. 성공은 payload 그대로(봉투 없음).
  - 본문 없는 성공(204)은 넷이다: `POST /auth/logout` · `DELETE /missions/{missionId}` · `POST /missions/{missionId}/feedback` · `POST /notifications/read`.
- 실패는 한 형태: `{"error": {"code": "RUN_IN_PROGRESS", "message": "…"}}`. 화면은 `code` 로 가르고 `message` 는 그대로 띄우지 않는다.
  - 도메인 예외뿐 아니라 Spring MVC 표준 예외(405 · 406 · 413 · 415 등)와 `/error` 경로(필터 예외 · 방화벽 거절)도 같은 봉투다. 405 에는 `Allow` 헤더가 붙는다.
  - 오류 응답의 Content-Type 은 `Accept` 와 상관없이 `application/json` 이다.
  - 400 의 `message` 에 Java 클래스 · 메서드 이름을 싣지 않는다. 본문을 못 읽으면 「요청 본문을 읽을 수 없습니다」, 경로 · 쿼리 값의 형이 틀리면 「'이름' 값의 형식이 올바르지 않습니다」 다.
  - `ErrorKind` → 상태 매핑과 표준 상태 → `code` 매핑은 `shared.web.ApiErrorHandler` 한 곳에만 있다.
- 거부(`refused: true`)는 오류가 아니다. HTTP 200.
- 값이 없으면 칸을 빼지 않고 `null` 로 싣는다(`spring.jackson.default-property-inclusion=always`). 모르는 값은 0 이 아니라 `null` 이다(백분위 · 등급 · 키 · 몸무게 · 달성률). 예외는 캘린더의 `rest` 하나다(쉬는 날일 때만 싣는다).
- 인증: `Authorization: Bearer <accessToken>`. 액세스 토큰 1시간, 리프레시 토큰 30일.
  공개 경로(`/api/v1/auth/**`)는 `Authorization` 헤더를 읽지 않는다. 만료 토큰이 실려 와도 google · refresh · logout · dev-login · review-login 은 401 이 나지 않는다.
- actor(로그인 계정 userId)와 대상 profileId 는 다르다. 부모가 아이 기록을 대리 입력한다. HTTP 요청의 role · familyId 값을 믿지 않고 저장된 프로필로 판단한다.
- 날짜 `YYYY-MM-DD`, 시각 ISO-8601, 달 `YYYY-MM`. 「오늘」 은 KST(Asia/Seoul)다(아래 「시각 · 날짜」). 한 주는 월요일에 시작한다.
- 문구 규칙: 「부족」·「미달」·「하위」 금지. `band`/`factor` 같은 코드값을 그대로 노출하지 말고 `copy`·`disclaimer`·`notice`·`headline` 같은 표시 문구는 고쳐 쓰지 않는다. 아이 화면에서 `parent_scope` 를 읽지 않는다.
- AI 서비스로 이름·생년월일·연락처·계정 식별자를 보내지 않는다. 프로필은 `profile_ref` 로만. 측정 항목 `005`·`006`(혈압)은 입력으로 받지 않는다(400 `ITEM_NOT_ALLOWED`).
- 보호자 동의가 필요한데 없거나 거둔 프로필(`consentRequired && !consentGiven`)은 새 기록에 넣지 않는다. 측정 등록 · 편성 대상 · 승인 · 미션 참여자 · 칸 끝 · 운동 느낌 · 활동 기록(타이머 · 걸음수 · 영상 진행)이 모두 422 `CONSENT_REQUIRED` 다. 지난 기록은 지우지 않는다.

### 권한 표기

각 주소 제목 뒤에 누가 부를 수 있는지 적는다. 판정은 `identity.api.FamilyAccess` 가 저장된 프로필로 한다.

| 표기 | 뜻 | 못 지나면 |
|---|---|---|
| 토큰 없이 | 공개 경로 | — |
| 로그인 | 유효한 액세스 토큰 | 401 `UNAUTHORIZED` |
| 같은 가족 | 호출 계정에 그 가족(또는 대상 프로필의 가족) 프로필이 붙어 있다 | 403 `NOT_SAME_FAMILY` |
| 보호자 | 같은 가족이고, 붙은 프로필이 PARENT 다 | 403 `NOT_SAME_FAMILY` · 403 `NOT_A_PARENT` |
| 자기 프로필 | 대상 프로필이 호출 계정에 붙어 있다 | 403 `FORBIDDEN` |
| 대신 | 자기 프로필이거나, 같은 가족 보호자가 **계정 없는 아이** 프로필을 대신한다(`Family.canActAs`). 계정 있는 아이 · 계정 없는 부모 자리는 대신하지 않는다 | 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN` |

- 가족 경로(`/families/{familyId}/…`)는 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` 차례로 본다.
- 프로필 경로(`/profiles/{profileId}/…`)는 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` 차례로 본다.
- 가족 쓰기(구성원 추가 · 프로필 고치기 · 참여 방식 · 동의 · 초대)는 프로필 행 낙관적 잠금(`profiles.version`)을 건다. 겹친 쓰기의 늦은 쪽은 409 `CONFLICT` 다. 가족을 다시 불러와서 다시 보내면 된다.
- 어느 주소든, 읽은 행을 다른 요청이 먼저 바꾸거나 지워 UPDATE · DELETE 가 0행이 되면(낙관적 잠금 충돌, `OptimisticLockingFailureException`) 409 `CONFLICT` 다. 다시 불러온 뒤 보내면 된다.

### 시각 · 날짜 (KST) 와 스케줄러

「오늘」 과 날짜 경계는 모두 `app.timezone`(기본 `Asia/Seoul`)이다. 시간은 `Clock` 빈으로만 읽는다.

| 무엇 | 규칙 |
|---|---|
| 편성 `date` | 오늘보다 앞이면 422 `INVALID_DATE` |
| 미션 만들기 | 시작일(또는 `dates` 의 어느 날)이 오늘보다 앞이면 422 `INVALID_DATE`. 오늘은 받는다 |
| 제안 승인 | 끝날이 오늘보다 앞인 항목은 미션으로 만들지 않는다 |
| 칸 끝 · 옛 타이머 | 오늘이 미션 기간 밖이면 422 `MISSION_NOT_ACTIVE`. 끝낸 날 = 서버가 받은 날 |
| 미션 지우기 | 끝날이 오늘보다 앞이면 409 `MISSION_ENDED` |
| 쉬는 날 카드 | 쓰기는 오늘부터 이번 달 끝까지, 되돌리기는 오늘과 앞날만 |
| 측정 | `testedOn` 이 오늘보다 뒤면 400. 나이는 `testedOn` 기준 만 나이 |
| 활동 날짜 | 칸 끝 · 옛 타이머는 서버가 받은 날(기기의 `startedAt` 이 아니다). 옛 걸음수만 보낸 `activityDate` |
| 캘린더 | `from` · `to` 가 1900-01-01 ~ 2100-12-31 밖이면 400 |
| 캘린더 스티커 | 붙인 시각의 KST 날짜에 선다(자정을 넘기면 다음 날) |
| `month` 가 없을 때 | 쉬는 날 카드 · 리그는 이번 달(KST) |

정해진 시각에 도는 일은 다섯이다. 모두 여러 번 돌아도 결과가 같다.

| 언제(KST) | 무엇 | 설정 키 | 기동 때 |
|---|---|---|---|
| 매일 04:00 | 리프레시 토큰 기록 정리 — 만료된 행, 폐기한 지 30일 지난 행을 지운다 | `app.auth.refresh-token-cleanup.cron` | 돌지 않는다 |
| 매일 07:30 | `MISSION_READY` 알림 — 오늘 서는 운동, 아이 프로필만. 오늘 기간이 걸친 미션이 있는 가족만 돈다 | `app.notification.mission-ready-cron` | 07:30 이 지났으면 그날 몫을 한 번 만든다 |
| 매일 09:00 | `REMEASURE` 알림 — 마지막 측정에서 30일 이상 지난 아이 → 부모 전원. 모든 가족을 돈다 | `app.notification.remeasure-cron` | 09:00 이 지났으면 한 번 만든다 |
| 매월 1일 00:10 | 리그 월초 정산 — 지난달 방을 정산하고 이번 달 방을 짠다 | `app.league.settle-cron` | 밀린 달을 따라잡는다 |
| 기동 때 + 223초마다 | 멈춘 편성 정리 — 223초 넘은 RUNNING 을 FAILED(`STALE`)로 바꿔 잠금을 푼다(`StaleCoachRunSweeper`) | 폴링 설정에서 계산 | 한 번 돈다 |

- 리그 정산은 1일 정산 전에 조회가 오면 그 자리에서 먼저 정산한다.
- 07:30 · 09:00 알림의 `createdAt` 은 제때 돈 실행이면 07:30 · 09:00 이다. 기동 따라잡기처럼 늦게 돈 실행은 실제로 만든 시각이다. 그래서 그 전에 받은 목록의 `upTo` 로 읽음 처리해도 보지 못한 알림은 읽음이 되지 않는다.
- 정해진 일을 도는 스레드는 2개다(`spring.task.scheduling.pool.size`). 기동 따라잡기는 알림 전용 스레드에서 돌아 기동을 늦추지 않는다.
- 서버를 여러 대로 띄울 때 스케줄러를 한 대만 돌리는 잠금(ShedLock)은 없다. 결과가 두 번 적히지는 않는다(조건부 UPDATE · 유니크 키).
- local · compose 에서는 서버 시계를 앞으로 옮길 수 있다(`POST /dev/clock`). 옮기는 동안 위 정해진 일을 원래 시각 차례대로 돌린다 — 며칠 · 몇 주 여정을 기다리지 않고 본다.

### 오류 코드

| 상태 | `code` | 뜻 · 나는 곳 |
|---|---|---|
| 400 | `BAD_REQUEST` | 필수 누락 · 형식 · 범위(Bean Validation), 본문 파싱 실패, 경로 · 쿼리 형 변환 실패, 필수 헤더 · 파라미터 누락, multipart 오류, 도메인 불변식(칸 규칙 · 목표 분 · 칸 없는 `TIMER_MINUTES` 목표 361분 이상 · 날짜 순서 · `endedAt ≤ startedAt` · 미래 날짜 · size 범위 · 캘린더 43일 이상 · 캘린더 날짜가 1900-01-01 ~ 2100-12-31 밖 · `dates` 와 `startDate` 같이 보냄 · 응원 모양 · 같은 측정 항목 두 번) |
| 400 | `NO_ITEMS` · `ITEM_NOT_ALLOWED` · `UNKNOWN_ITEM` · `ITEM_OUT_OF_RANGE` | 측정 등록 — 항목 0개 · 혈압(005 · 006) · 모르는 코드 · `range` 밖 값 |
| 400 | `INVALID_SLOT` | 운동할 수 있는 시간 바꾸기 — 칸 값 규칙 위반 |
| 400 | `PROFILE_REQUIRED` | 운동 구간 찜 · 찜 목록에 `profileId` 가 없음 |
| 401 | `UNAUTHORIZED` | 토큰 없음 · 만료 · 서명 불일치 |
| 401 | `INVALID_REFRESH_TOKEN` | `auth/refresh` — 검증 실패 · 기록 없음 · 이미 폐기(재사용) · 계정이 ACTIVE 아님 |
| 401 | `GOOGLE_AUTH_FAILED` | `auth/google` |
| 403 | `NOT_SAME_FAMILY` | 다른 가족의 리소스 |
| 403 | `NOT_A_PARENT` | 보호자 전용 주소를 아이 계정이 부름. 아이가 칭찬(PRAISE)을 보냄. 아이 계정이 캘린더에서 남의 기록을 봄 |
| 403 | `NOT_A_PARTICIPANT` | 미션 참여자가 아님 |
| 403 | `FORBIDDEN` | 대신할 수 없는 프로필(응원 `fromProfileId` · 칸 끝 · 느낌 · 알림함 · 옛 타이머 · 걸음수 · 영상 진행 · 코치 대화), 자기 프로필이 아님(참여 방식), 계정이 붙은 다른 사람 프로필 고치기, 다른 프로필의 대화, Spring Security 거절 |
| 403 | `SELF_CONSENT` | 자기 프로필의 동의를 바꿈 |
| 404 | `NOT_FOUND` | 없는 경로 |
| 404 | `FAMILY_NOT_FOUND` · `PROFILE_NOT_FOUND` · `MISSION_NOT_FOUND` · `SESSION_NOT_FOUND` · `COACH_RUN_NOT_FOUND` · `VIDEO_NOT_FOUND` · `CLIP_NOT_FOUND` · `CONVERSATION_NOT_FOUND` · `CODE_NOT_FOUND` · `CHEER_NOT_FOUND` | 각 리소스가 없음. 응원의 `missionId` 가 이 가족 미션이 아니어도 `MISSION_NOT_FOUND` |
| 404 | `NOT_REST_DAY` | 쉬는 날이 아닌 날을 되돌림 |
| 404 | `LEAGUE_NOT_FOUND` | 지난달 리그 방에 없던 가족 |
| 405 · 406 · 413 · 415 | `METHOD_NOT_ALLOWED` · `NOT_ACCEPTABLE` · `CONTENT_TOO_LARGE` · `UNSUPPORTED_MEDIA_TYPE` | Spring MVC 표준 예외(RFC 9110 상태 이름) |
| 409 | `CONFLICT` | 유니크 제약 위반(동시 요청이 사전 검사를 함께 지나친 경우), 낙관적 잠금 충돌(가족 쓰기 · 읽은 행을 다른 요청이 먼저 바꾸거나 지움), 쉬는 날 카드 경합이 세 번 연달아 남. NOT NULL · FK 위반은 500 |
| 409 | `ALREADY_IN_FAMILY` · `ALREADY_CLAIMED` · `ALREADY_MEMBER` | 가족 만들기 · 초대(한 계정 한 가족) |
| 409 | `DUPLICATE_DATE` | 같은 프로필 같은 날짜 측정 |
| 409 | `RUN_IN_PROGRESS` | 같은 (대상, 날짜)의 편성이 RUNNING |
| 409 | `ALREADY_APPROVED` · `INVALID_STATE` | 이미 승인 / 그 밖으로 끝난 편성을 다시 결정 |
| 409 | `PROPOSAL_EXPIRED` | 승인할 제안 항목의 기간이 전부 지남 |
| 409 | `MISSION_ENDED` · `MISSION_ALREADY_STARTED` | 미션 지우기 — 기간이 끝남 · 누가 칸을 끝냈거나 완료됨 |
| 409 | `ALREADY_THANKED` | 같은 칭찬 스티커에 고마워요를 두 번 |
| 409 | `ALREADY_REST_DAY` · `NO_REST_CARD_LEFT` | 쉬는 날 카드 — 이미 쉬는 날 · 이달 카드 두 장을 다 씀 |
| 409 | `LEAGUE_BUSY` | 리그 방 배정이 다섯 번 연달아 겹침. 다시 부르면 된다 |
| 410 | `CODE_EXPIRED` | 초대코드 기한 지남 |
| 422 | `CONSENT_REQUIRED` | 보호자 동의 없음 · 거둠(0장 공통 규칙) |
| 422 | `CONSENT_NOT_APPLICABLE` | 동의를 바꿀 대상이 보호자(PARENT)임. 보호자 동의는 아이 프로필에만 있다 |
| 422 | `UNDER_14_NOT_ALLOWED` | 만 14세 미만이 가족을 만들거나 · PARENT 로 들어가거나 · 동의를 기록함. PARENT 생일을 만 14세 미만으로 고침 |
| 422 | `NOT_MEASURABLE` · `ITEM_NOT_FOR_AGE_GROUP` | 측정 — 만 4세 미만 · 연령대 항목 아님 |
| 422 | `NO_MEASURED_MEMBER` · `INVALID_DATE` · `NOT_FAMILY_MEMBER` | 편성 · 미션 · 응원 · 쉬는 날 · 리그. `INVALID_DATE` 는 지난 날짜 · 틀린 날짜 · 앞 달 |
| 422 | `MISSION_NOT_ACTIVE` · `TOO_SHORT` | 칸 끝 · 옛 타이머 — 오늘이 기간 밖. 칸 끝 — 인정 초가 칸 시간의 절반 미만 |
| 422 | `INVALID_METRIC` · `TARGET_NOT_REACHED` | 활동 기록(목표 지표가 다름) · 보호자 확인(걸음수 목표 미도달) |
| 422 | `NOT_APPLICABLE` | 아이 프로필의 참여 방식을 바꿈 |
| 422 | `SELF_CHEER` · `CHEER_KIND_NOT_ALLOWED` · `NOT_A_REPLY_TARGET` | 응원 — 자기에게 · 종류와 방향이 안 맞음 · 고마워요가 답할 칭찬이 아님 |
| 422 | `ALREADY_MOVED` | 쉬는 날 카드 — 그날 아이가 이미 운동함 |
| 429 | `TOO_MANY` | 응원: (보낸 프로필, 받는 프로필) 분당 5회 초과. 초대코드: 없는 코드를 10분에 10번 넘게 넣음. 심사용 계정 로그인: 같은 IP(IPv6 는 /64)에서 한 시간에 30번, 또는 모두 합쳐 한 시간에 300번을 넘김. 편성 시작: 심사용 계정이 하루 20번을 넘김 |
| 500 | `INTERNAL_ERROR` | 처리하지 못한 예외 |
| 503 | `TEMPORARILY_UNAVAILABLE` | AI 연결 실패 · 시간 초과 · 5xx · 200 인데 응답을 읽지 못함(깨진 JSON · text/html · 칸 누락)(대화), 비동기 요청 시간 초과 |
| 503 | `AI_BAD_REQUEST` | AI 가 400 을 냄(서버가 잘못 보낸 것) — 대화 |

- AI 가 내는 `RUN_IN_PROGRESS`(409) · `RUN_NOT_FOUND`(404)는 편성 실행기 안에서만 쓰이고 클라이언트로 나가지 않는다. 편성은 202 로 접수된 뒤라, 실패는 `CoachRunView.failureCode` 로 알린다(4장).
- FE 요청서 7장의 「화면이 가르는 코드」 중 서버가 내지 않는 것: `CONSENT_WITHDRAWN`(만들지 않는다 — 동의를 거둬도 `CONSENT_REQUIRED`) · `ALREADY_RUN_THIS_WEEK`(없앴다).

### 구현 상태 (2026-09-29 · `feature/BE-35-launch-readiness`)

- 경로 46개, 메서드까지 세면 53개(아래 「주소 목록」). 전환기 별칭(경로 5개 · 메서드 6개)은 세지 않았다. `POST /auth/dev-login` 은 local · compose · test 프로필에서만 있다. `POST /auth/review-login` 은 `app.auth.review-login.enabled` 가 켜진 곳(local · compose · prod)에만 있다.
  Notion 명세의 `GET /facilities` 는 범위 밖(공공데이터 출처 미확정).
- 묶음마다 바뀐 것
  - 1차(PR #4~#11): 편성이 「아이 한 명의 하루」 가 됐다. 미션 칸 저장 · 조회, 미션 단건. 측정 등급 85/65/40 · 측정 이력 · 레이더 민첩성. `ProfileSummary.sex`. 계정 없는 아이 이름으로 응원. 모든 오류가 봉투로. AI 영상 48편 · 구간 695개(`V132`).
  - 2차(PR #12~#16): 제안 칸 저장 · 칸 분 배분 · 승인 때 미션 칸으로 복사. 응원 종류 · 스티커 · 고마워요 · 받은 응원 목록. 쉬는 날 카드. 리프레시 토큰 회전 · 로그아웃. AI 실행 실패도 대체 편성 · `failureCode` · `notices`.
  - 3차(PR #18~#23): progress 모듈(경험치 · 레벨 · 업적 · 이어서 한 날). 운동 구간 목록 · 찜(`/exercises`). 운동할 수 있는 시간. 초대코드 미리 보기 · 시도 제한 · 한 계정 한 가족 · `selfProfileId`. 측정은 보호자만 · 아이 계정에 부모만 볼 값 비우기 · 값 범위 검사. 리프레시 토큰 기록 정리.
  - 4차(PR #24~#26): 운동 한 칸 끝 · 끝낸 칸 기준 진행도 · 활동 초 단위 · 경험치 연결. 프로필 고치기 · 동의 이력 · 만 14세 경계 · 가족 쓰기 낙관적 잠금. league 모듈(월 단위 달성률 · 다섯 티어 · 월초 정산).
  - 5차(PR #27~#29): 미션 지난 날짜 막기 · 지우기 · 여러 날 한 번에 · 운동 느낌. 가족 캘린더. notification 모듈(알림함).
  - QA 수정(PR #31~#33): 칸 끝이 미션 행을 잠금 · 칸 없는 분 목표 360분 상한 · 옛 주소와 예측 권한을 「대신」으로 · `canApprove` 에 참여자 동의 · latest 가 미션을 모두 지운 승인 회차를 건너뜀. OpenAPI null 표시 · 낙관적 잠금 409 · AI 응답 해석 실패도 대체 편성 · 보호자(PARENT) 동의 막기(`V151`) · 동시 스티커 · 초대로 붙은 보호자의 `SUPPORT_MODE`. 알림을 커밋 뒤 전용 스레드에서 쓰기 · 다시 재면 `REMEASURE` 지우기 · 쉬는 날 `MISSION_READY` 거르기.
  - 출시 준비(BE-35): FE 이름 전환기 별칭 · 10년 예측 걷음(`V153`) · 항목 등급을 백분위 85/65/40 대신 국민체력100 공식 기준표로(`V154`) · 유소년 044 벽패스 항목 · 규준(`V155`) · 같은 값이 몰린 규준은 가운데 백분위 · 백분위를 AI 또래 분포 표와 계산식으로(`V156` · 예전 표 걷음 `V157`) · 측정에 체지방률 · 허리둘레(`V158`) · 등급을 인증서처럼 한 사람에게 하나로 · 또래 등급 비율(`V159`) · 항목 등급 칸 걷음(`V160`) · 공단 「국민체력100 동영상 정보」 오픈API 영상 890편을 영상 후보에 더함(`V161`, 응답에 `mediaUrl` · `thumbnailUrl`) · 심사용 계정 로그인(`POST /auth/review-login` — 부를 때마다 새 계정 · 체험 가족, IP 마다 한 시간 30번 · 모두 합쳐 300번, prod 는 X-Forwarded-For 를 읽음).
- 없앤 것: 일요일 20시 자동 주간 편성(`CoachRunScheduler` · `app.coach.schedule.cron`), `ALREADY_RUN_THIS_WEEK`, 422 `NOT_PARTICIPANT`(→ 403 `NOT_A_PARTICIPANT`).
  10년 예측(2026-09-16 결정 · FE 도 걷음): `POST /profiles/{id}/predictions` · AI `fitness/trajectory` 호출 · `predictions` · `prediction_points` 표(`V153`) · 422 `NO_FITNESS_TEST` · 404 `FITNESS_TEST_NOT_FOUND`. 개인 시계열이 없어 측정 이력 추이로 대신한다.
- 명세와 다르게 정한 것: 코치 제안 `participants[]` 에 편성 역할 `coachRole`(주행자 · 동반자 · 응원)을 두고 `role` 은 프로필 역할(PARENT/CHILD). 영상 목록 항목에 `badges`. 쉬는 날 경로는 `rest-cards`, 칸 끝은 `/sessions/{seq}/complete`, 구간 목록은 `/exercises`(FE 요청서 0장 합의의 설계안 이름).
  - **전환기 별칭**: 지금 FE 는 `rest-days` · `/sessions/{seq}/done` · `/clips` 를 부른다(fe:src/lib/api/queries.ts). 그 이름으로 부르면 실제 BE 에서 404 가 나서(칸 끝 기록 실패 · 쉬는 날 카드 · 운동 찾기), 계약 이름은 그대로 두고 FE 이름도 **같은 핸들러**로 받는다. 요청 · 응답 · 권한 · 오류 코드가 계약 이름과 똑같다. OpenAPI 에는 `deprecated` 로 싣는다(`OpenApiConfig.TRANSITIONAL_ALIASES`). FE 가 계약 이름으로 옮기면 걷는다. 목록은 아래 「전환기 별칭」 표. 같은 성격의 선례: 응원 `emoji`(↔ `stickerId`), 쉬는 날 요청 `restDate`(↔ `date`), 경험치 줄 `reason` · `at`(5장).
- **FE 가 부르지 않음 · 걷을 후보**: `POST /coach/chat` · `GET /families/{id}/report/weekly` · `GET /videos` · `POST /videos/{id}/favorite` · `POST /videos/{id}/progress` · `POST /missions/{id}/activity/timer` · `POST /missions/{id}/activity/steps` · `POST /missions/{id}/participants/{profileId}/confirm`. 아래 각 절 제목에도 같은 표시를 붙였다.
  - 이 경로로 끝난 미션은 `MissionCompleted` 를 내지 않는다. 그래서 그날 `MISSION_READY` 알림이 남는다. 이 경로는 미션 행을 잠그지 않는다.
  - 코치 대화 · 영상 진행 · 타이머 · 걸음수는 「대신」 규칙을 지나야 한다. 같은 가족이어도 계정이 붙은 다른 식구 이름으로는 403 `FORBIDDEN` 이다.
  - 칸 있는 미션은 타이머 · 영상 진행으로 분을 쌓아도 진행되지 않는다(진행도는 끝낸 칸 기준).

### 주소 목록 (경로 47개 · 메서드 55개)

| 모듈 | 메서드 | 경로(`/api/v1` 뒤) | 권한 | 성공 |
|---|---|---|---|---|
| identity | POST | `/auth/google` | 토큰 없이 | 200 |
| identity | POST | `/auth/refresh` | 토큰 없이 | 200 |
| identity | POST | `/auth/logout` | 토큰 없이 | 204 |
| identity | POST | `/auth/dev-login` | 토큰 없이(local · compose · test) | 200 |
| identity | POST | `/auth/review-login` | 토큰 없이(local · compose · prod) | 200 |
| (개발용) | GET · POST | `/dev/clock` | 로그인(local · compose) | 200 · 200 |
| identity | GET | `/me` | 로그인 | 200 |
| identity | POST | `/families` | 로그인 | 201 |
| identity | POST · GET | `/families/{familyId}/profiles` | 보호자 · 같은 가족 | 201 · 200 |
| identity | PATCH | `/profiles/{profileId}` | 보호자(계정 없는 프로필 · 자기 프로필) | 200 |
| identity | PATCH | `/profiles/{profileId}/support-mode` | 자기 프로필(PARENT) | 200 |
| identity | PATCH | `/profiles/{profileId}/consent` | 보호자(자기 프로필 제외) | 200 |
| identity | POST | `/profiles/{profileId}/invite` | 보호자 | 201 |
| identity | GET | `/invites/{claimCode}` | 로그인 | 200 |
| identity | POST | `/profiles/claim` | 로그인 | 200 |
| identity | GET · PUT | `/profiles/{profileId}/availability` | 같은 가족 · 보호자 | 200 · 200 |
| identity | POST · GET | `/families/{familyId}/cheers` | 같은 가족(보내는 프로필은 대신) · 같은 가족 | 201 · 200 |
| fitness | GET | `/fitness/items` | 로그인(`profileId` 를 주면 보호자) | 200 |
| fitness | POST · GET | `/profiles/{profileId}/fitness-tests` | 보호자 · 같은 가족 | 201 · 200 |
| fitness | GET | `/profiles/{profileId}/fitness-tests/latest` | 같은 가족 | 200 |
| fitness | GET | `/families/{familyId}/fitness-map` | 같은 가족 | 200 |
| activity | GET · POST | `/families/{familyId}/rest-cards` | 같은 가족 · 보호자 | 200 · 201 |
| activity | DELETE | `/families/{familyId}/rest-cards/{restDate}` | 보호자 | 200 |
| coaching | POST | `/families/{familyId}/coach/runs` | 보호자 | 202 |
| coaching | GET | `/families/{familyId}/coach/runs/latest` | 같은 가족 | 200 |
| coaching | GET | `/coach/runs/{runId}` | 같은 가족 | 200 |
| coaching | POST | `/coach/runs/{runId}/approve` | 보호자 | 200 |
| coaching | POST | `/coach/runs/{runId}/reject` | 보호자 | 200 |
| coaching | POST · GET | `/families/{familyId}/missions` | 보호자 · 같은 가족 | 201 · 200 |
| coaching | GET · DELETE | `/missions/{missionId}` | 같은 가족 · 보호자 | 200 · 204 |
| coaching | POST | `/missions/{missionId}/sessions/{seq}/complete` | 대신 + 참여자 | 200 |
| coaching | POST | `/missions/{missionId}/feedback` | 대신 + 참여자 | 204 |
| coaching | POST | `/missions/{missionId}/participants/{profileId}/confirm` | 보호자 · FE 가 부르지 않음 | 200 |
| coaching | POST | `/missions/{missionId}/activity/steps` | 대신 + 참여자 · FE 가 부르지 않음 | 200 |
| coaching | POST | `/missions/{missionId}/activity/timer` | 대신 + 참여자 · FE 가 부르지 않음 | 200 |
| coaching | GET | `/families/{familyId}/calendar` | 보호자는 식구 누구나 · 아이 계정은 자기 것만 | 200 |
| coaching | GET | `/exercises` | 로그인(`profileId` 를 주면 같은 가족) | 200 |
| coaching | POST | `/exercises/{exerciseId}/favorite` | 같은 가족 | 200 |
| coaching | GET | `/videos` | 로그인 · FE 가 부르지 않음 | 200 |
| coaching | POST | `/videos/{videoId}/favorite` | 같은 가족 · FE 가 부르지 않음 | 200 |
| coaching | POST | `/videos/{videoId}/progress` | 대신 · FE 가 부르지 않음 | 200 |
| coaching | POST | `/coach/chat` | 대신 · FE 가 부르지 않음 | 200 |
| coaching | GET | `/families/{familyId}/report/weekly` | 같은 가족 · FE 가 부르지 않음 | 200 |
| progress | GET | `/profiles/{profileId}/progress` | 같은 가족 | 200 |
| league | GET | `/families/{familyId}/league` | 같은 가족 | 200 |
| notification | GET | `/notifications` | 대신 | 200 |
| notification | POST | `/notifications/read` | 대신 | 204 |

### 전환기 별칭 (deprecated · FE 가 계약 이름으로 옮기면 걷는다)

지금 FE 가 부르는 이름이다. 계약 이름과 같은 핸들러라 요청 · 응답 · 권한 · 오류가 똑같다. 새로 부르는 쪽은 계약 이름을 쓴다.
OpenAPI(`/v3/api-docs`)에는 `deprecated: true` 로 싣고, operationId 는 `<메서드 이름>_transitional`(예: `completeSession_transitional`)이라 계약 경로의 operationId 는 별칭이 없을 때와 같다.

| 별칭(`/api/v1` 뒤) | 메서드 | 같은 핸들러의 계약 이름 |
|---|---|---|
| `/families/{familyId}/rest-days` | GET · POST | `/families/{familyId}/rest-cards` |
| `/families/{familyId}/rest-days/{restDate}` | DELETE | `/families/{familyId}/rest-cards/{restDate}` |
| `/missions/{missionId}/sessions/{seq}/done` | POST | `/missions/{missionId}/sessions/{seq}/complete` |
| `/clips` | GET | `/exercises` |
| `/clips/{exerciseId}/favorite` | POST | `/exercises/{exerciseId}/favorite` |

### 공통 타입
| 이름 | 값 |
|---|---|
| `Role` | `PARENT` · `CHILD` — 프로필 생성 시 확정, 초대받는 쪽이 못 고침 |
| `SupportMode` | `CHEER_ONLY` · `WEEKEND` · `FULL` — PARENT만 |
| `AgeGroup` | `유아기`(만 0~6; 만 4세 미만은 measurable=false) · `유소년`(7~12) · `청소년`(13~18) · `성인`(19~64) · `어르신`(65+) |
| `Sex` | `M` · `F` |
| `FitnessFactor` | `심폐지구력` · `근력` · `근지구력` · `유연성` · `민첩성` · `순발력` · `협응력` · `평형성` — 와이어 값은 한글 라벨. 요청에서는 영문 이름(`FLEXIBILITY` 등)도 받는다 |
| `Band` | `strength`(백분위 ≥75) · `steady`(25~75) · `growth`(<25) · `null`(측정값 없음) |
| `Grade` | `1등급` · `2등급` · `3등급` · `참가` 넷뿐(4 · 5등급 없음). 인증서처럼 한 사람(회차)에 하나를 국민체력100 **공식 등급 기준표**로 정한다(아래 「백분위·등급 계산」). 항목마다 매기지 않는다. 판정하지 못하면 `null` |
| `CertificationStatus` | `GRADED`(등급 있음, 참가 포함) · `NEEDS_ITEMS`(기준 줄은 있는데 어느 등급도 판정하지 못함) · `NO_CRITERIA`(그 나이 · 성별 기준 줄이 없음 — 만 7~10세 · 어르신 등) |
| `NextStep` | `CREATE_FAMILY` · `CLAIM` · `HOME` · `SUPPORT_MODE` |
| `CheerKind` | `DONE`(아이 → 부모 「다 했어요」) · `PRAISE`(부모 → 아이 칭찬) · `THANKS`(아이 → 부모 고마워요) |
| `TargetMetric` | `VIDEO_DONE` · `TIMER_MINUTES` · `STEPS` — `STEPS`만 `serverVerifiable=false` |
| `ActivitySource` | `MANUAL` · `TIMER` · `VIDEO` — `TIMER`·`VIDEO`만 `serverVerified=true` |
| `VerifiedBy` | `VIDEO_PROGRESS` · `TIMER` · `SELF_REPORT` |
| `ItemCode` | 측정 항목 3자리 코드. 코드가 식별자, 이름은 표기 |
| `InviteStatus` | `NONE` · `ISSUED` · `EXPIRED` · `CLAIMED` |
| `MissionOrigin` | `COACH` · `MANUAL` |
| `MissionStatus` | `ACTIVE` · `DONE` · `EXPIRED` |
| `MissionScope` | `ALL` · `MINE` · `FAMILY` |
| `SessionPhase` | `WARMUP` · `MAIN` · `COOLDOWN` — 미션 칸의 단계(준비 · 본 · 정리) |
| `Feel` | `EASY` · `GOOD` · `HARD` — 운동 느낌 |
| `CoachRunStatus` | `RUNNING` → `AWAITING_APPROVAL` → `APPROVED` / `REJECTED`; `RUNNING` → `FAILED` |
| `CoachRunFailureCode` | `NO_CITATIONS` · `AI_FAILED` · `CONSENT_REQUIRED` · `BUSY` · `STALE` · `ERROR` — FAILED 일 때만(4장) |
| `CoachRole` | `주행자` · `동반자` · `응원` — 편성 역할(문자열) |
| `CoachPlace` | `HOME` · `OUTDOOR` — 편성 조건의 장소. `null` 이면 가리지 않는다 |
| `TriggerType` | `MANUAL`. `SCHEDULE` 은 없앤 자동 편성이 남긴 옛 행에만 있다 |
| `FitnessTestSource` | `SELF_INPUT` · `CENTER_SHEET` |
| `InputGroup` | `EASY`(집에서 되는 항목, 필수) · `EQUIPMENT`(장비·공간 필요, 선택) |
| `XpKind` | `SESSION_DONE`(+5) · `MISSION_DONE`(+20) · `STICKER`(+10) · `REMEASURE`(+20) |
| `LeagueTier` | `BRONZE` · `SILVER` · `GOLD` · `PLATINUM` · `DIAMOND` |
| `NotificationKind` | `KID_DONE` · `KID_THANKS` · `PRAISE` · `MISSION_READY` · `ACHIEVEMENT` · `REMEASURE` |

### ProfileSummary (identity 가 내보내는 유일한 공개 언어)
```
{profileId, familyId, name, role, ageGroup, sex, hasAccount, inviteStatus, supportMode|null, measurable, consentRequired, consentGiven}
```
- `sex` 는 `M` · `F`. 화면이 「엄마」「아빠」 로 부를 때 쓴다.
- `consentRequired` = 만 14세 미만이거나, 동의를 거둔 채다. 거둔 동의는 만 14세가 지나도 풀리지 않고 보호자가 다시 동의해야 풀린다.
- `consentGiven` = 동의가 필요 없거나(위가 false), personal · health 둘 다 동의했고 거두지 않았다.
- `measurable` = 만 4세 이상 AND `consentGiven`.
- 싣는 응답: 가족 구성원 목록 · `/me` · 로그인 · 리프레시 · 가족 만들기 · 구성원 추가 · 프로필 고치기 · 참여 방식 변경. `fitness-map` 의 `members[]` 도 같은 칸을 싣는다.
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
| 044 | 눈-손협응력(벽패스) | 회 | ↑ | 협응력 |
| 050 | 5m4회왕복달리기 | 초 | ↓ | 민첩성 |
| 051 | 3x3버튼누르기 | 초 | ↓ | 협응력 |
- 입력 금지: 005 이완기혈압 · 006 수축기혈압 → 400 `ITEM_NOT_ALLOWED`. 신체조성(003·004·018·042)은 `items` 로 받지 않고(400 `UNKNOWN_ITEM`) 백분위도 내지 않는다. 체지방률(003) · 허리둘레(004)는 측정 등록의 `bodyFatPct` · `waistCm` 칸으로 받아 그 회차에 굳힌다 — 인증 3등급의 신체조성 관문(BMI · 체지방률 · 허리둘레-신장비)에 쓴다. 044 는 유소년의 벽패스(벽에 공을 던지고 받은 횟수)로, 청소년 017(초)과 다른 협응력 시험이다(AI `common/items.py` 와 같다).
- 연령대별 항목 (AI 팀 기준표 열 매핑 `CRITERIA_COLUMNS` 기준):
  - 유아기(4~6): 020(10m 왕복오래달리기) · 028 · 009 · 012 · 050 · 022 · 051
  - 유소년(7~12): 020(15m 왕복 오래달리기) · 028 · 009 · 012 · 043 · 022 · 044(벽패스) — AI `AGE_GROUP_ITEMS` 유소년과 같다
  - 청소년(13~18): 020(20m 왕복 오래달리기) · 035/037 · 028 · 009 · 010 · 012 · 013 · 014 · 017
  - 성인(19~64): 020(20m) · 035/037 · 028 · 019 · 012 · 021 · 040 · 022 · 041
  - 어르신(65+): ▲ 기준항목 미정 — 012 · 028 · 019 만 잠정
- `inputGroup`: EASY = 009 · 010 · 012 · 014 · 019 · 041 · 043 (장비 없이 집에서). EQUIPMENT = 028(악력계) · 020 · 022 · 050 · 021 · 013 (공간) · 035 · 037 · 040 · 017 · 051 (장비) · 044(벽·공).
- `range`: 009 0~120 · 010 0~120 · 012 -30~40 · 013 5~60 · 014 0~2 · 017 0~120 · 019 0~120 · 020 0~150 · 021 5~60 · 022 0~350 · 028 0~150 · 035/037 10~90 · 040 0~5 · 041 0~2 · 043 0~120 · 044 0~60 · 050 5~60 · 051 0~60. 서버도 이 범위로 검사한다(밖이면 400 `ITEM_OUT_OF_RANGE`).

### 백분위·등급 계산 (`PeerTable` · `GradeTable`)
- 또래 분포 표 `fitness_value_quantiles`(`V156`, AI `data/release/value_quantiles.csv` 1,746줄)를 부팅 때 메모리에 올린다. 한 줄은 (연령대 · 성별 · 나이 · 항목)의 표본 수 `n` 과 0~100 백분위 값 101칸이다. 나이는 유아기만 개월(48~83), 나머지는 만 나이다. 연령대는 측정일 만 나이로 정한다(7 미만 유아기 · 13 미만 유소년 · 19 미만 청소년 · 65 미만 성인 · 그 위 어르신 — AI `age_group_of` 와 같다).
- 백분위는 AI `stats/tables.py` 의 `peer` · `percentile_of` 를 그대로 옮겼다. 아래쪽 자리 `low`(값 이상인 첫 칸, `searchsorted left`)와 위쪽 자리 `high`(값보다 큰 첫 칸, `right`)의 가운데 `(low + high) / 2` 를 쓰고, 낮을수록 좋은 항목(013 · 017 · 021 · 040 · 050 · 051, AI `lower_is_better` 와 같다)은 `100 - 자리` 로 뒤집는다. 짝수 쪽으로 반올림(파이썬 `round`)한 뒤 0~100 으로 자른다 — 0 과 100 도 나온다.
  같은 값이 몰린 항목도 가운데 자리라 순위가 튀지 않는다(044 벽패스는 여아 11세의 0~35번째 칸이 0회라 0회 → 18).
- 표본이 30 에 못 미치거나(`n < 30`, AI `MIN_SAMPLE`) 칸이 없으면 `null` 이다. 만 7~10세는 공공데이터에 측정이 없어 `null`. 어르신은 012 · 028 칸이 있다(표본이 모자란 아주 높은 나이는 `null`). 유아 48~59개월 009 도 있다.
- 서버와 AI 가 같은 값에 같은 백분위를 내는지는 `AiPercentileParityTest` 가 못박는다. 기대값(`src/test/resources/fitness/ai-percentiles.csv`)은 `scripts/ai_percentile_fixture.py` 가 AI 코드를 직접 불러 낸 것이고, 표의 칸마다 분위 점 위 · 사이 · 범위 밖 · 같은 값이 몰린 곳을 넣었다.
- 백분위는 저장 시점 값으로 굳힌다(표가 바뀌거나 프로필 생일이 바뀌어도 지난 회차는 그대로). `band`(≥ 75 `strength` · ≥ 25 `steady` · 그 밖 `growth`, AI `band_of` 와 같다) · `topPercentText` 는 응답 때 굳힌 백분위에서 다시 셈한다(계산은 한 군데).
- 예전 백분위 표 `fitness_norms`(`V2` · `V3` · `V155`, 21개 점 사이를 선형 보간하고 1~99 로 자름)는 `V157` 로 걷었다. 그 전에 저장된 회차의 백분위는 다시 셈하지 않았다(출시 전이다). 데모 시드 회차는 새 식으로 맞췄다.
- **등급은 인증서처럼 한 사람에게 하나다.** 회차마다 `certification` 하나를 매기고 항목마다 매기지 않는다. 백분위에서 셈하지도 않는다. 규칙은 AI `stats/tables.py` 의 `certify` 를 한 줄씩 옮겼다(`Certifier` · `GradeTable`). 기준표는 국민체력100 공식 기준 `fitness_grade_thresholds`(`V154`, AI `data/release/grade_thresholds.csv` 1,122줄)다.
  - 기준 한 줄 = (연령대 · 성별 · 나이 구간 · 등급 · 항목)의 `op` · `cutoff`. 뜻은 AI `Threshold.passes()` 와 같다: `>=` · `<=` 는 경계 포함, `<` 는 경계 제외, `between` 은 `cutoff ≤ 값 ≤ cutoff_upper`. 낮을수록 좋은 항목(013 · 017 · 021 · 040 · 050 · 051)은 `<=` 줄이다.
  - 나이 구간은 유아기만 개월(48~53 · 54~59 · 60~65 · 66~71 · 72~83), 나머지는 세(유소년 11 · 12, 청소년 13~18 한 살씩, 성인 5~6세 폭). 측정일 기준 나이로 고른다.
  - 판정 값 = 잰 항목 + 체지방률(003) + 허리둘레(004) + BMI(018 = 몸무게 ÷ (키 m)², 소수 둘째 자리) + 허리둘레-신장비(042 = 허리둘레 ÷ 키 cm, 소수 셋째 자리). AI `assess._with_body` 와 같고, BMI · 허리둘레-신장비는 파이썬처럼 double 로 셈한 뒤 짝수 쪽으로 반올림한다.
  - 1 → 2 → 3등급 차례로, 그 등급 줄이 보는 항목을 **다 쟀으면** 판정하고 다 넘으면 그 등급이다. 하나라도 안 쟀으면 그 등급으로는 판정하지 않는다(집에서 두어 개만 잰 사람을 맨 아래로 내리지 않으려는 AI 규칙). 한 등급이라도 판정했는데 다 못 넘으면 `참가`, 한 등급도 판정하지 못했으면 `null`.
  - 035 · 037(VO2max 트레드밀 · 스텝)은 둘 중 하나만 재면 된다. 둘 다 쟀으면 둘 다 넘어야 한다(AI 와 같다).
  - 3등급 줄은 운동 항목 일부와 신체조성을 같이 본다 — 유아기 BMI, 유소년 BMI · 허리둘레-신장비, 청소년 BMI · 체지방률, 성인 BMI · 체지방률(사이 값). 그래서 키 · 몸무게와 체지방률 또는 허리둘레를 적지 않으면 3등급은 판정되지 않는다.
  - 예: 유소년 여 만 11세 1등급은 020 ≥ 62회 · 028 ≥ 44.4% · 009 ≥ 36회 · 012 ≥ 10.9cm · 043 ≥ 32회 · 022 ≥ 165cm · 044 ≥ 19회를 모두 재고 모두 넘어야 한다. 3등급은 020 ≥ 40 · 028 ≥ 34.8 · 009 ≥ 18 · 012 ≥ 3.0 · BMI < 23.3 · 허리둘레-신장비 < 0.47.
  - `status` 는 `CertificationStatus`(위 공통 타입)다.
  - `missingItems`: `NEEDS_ITEMS` 면 모자란 것이 가장 적은 등급의 것(같으면 높은 등급). `GRADED` 인데 1등급을 판정하지 못해서 나온 결과면 1등급에 모자란 것, 그 밖에는 `[]`. 한 칸은 사람이 한 번에 재는 것 하나다 — 035 · 037 은 한 칸(`"itemCodes":["035","037"]`, `label` `트레드밀VO2max 또는 스텝검사VO2max`), 018 은 `키 · 몸무게`, 042 는 `허리둘레`(키도 없으면 `키 · 허리둘레`), 003 은 `체지방률`, 나머지는 그 연령대 항목 이름(`FitnessItem.label`)이다. 첫 코드 차례로 준다.
  - `peers`: 같은 (연령대 · 성별 · 나이) 참가자의 인증 등급별 비율 `{grade, ratio}` 넷, 1등급 · 2등급 · 3등급 · 참가 차례. 표는 `fitness_grade_distribution`(`V159`, AI `data/release/grade_distribution.csv` 1,048줄 — 국민체력100 인증 결과 원자료 `CRTFC_FLAG_NM` 를 AI 가 센 것으로 AI `peer_distribution` 과 같다)이고, 없으면 `[]`. 성인 · 청소년은 2025년 6월부터 1~6등급 체계로 바뀌어 네 비율의 합이 1 에 못 미칠 수 있다 — 그대로 준다. 예: 유소년 여 만 11세는 1등급 0.0322 · 2등급 0.0983 · 3등급 0.2282 · 참가 0.6413.
  - 등급은 저장하지 않고 등록 · latest 응답 때 셈한다. 기준표가 고정이라 같은 회차는 늘 같은 등급이다. 저장된 측정값 · 키 · 몸무게 · 체지방률 · 허리둘레, 측정일 나이(유아기는 개월), 프로필 성별로 셈한다.
  - 서버와 AI 가 같은 등급을 내는지는 `AiCertifyParityTest` 가 못박는다. 기대값(`src/test/resources/fitness/ai-certify.csv`)은 `scripts/ai_certify_fixture.py` 가 AI 코드(`_with_body` · `certify`)를 직접 불러 낸 것이고, 기준 줄이 있는 (연령대 · 성별 · 나이) 180칸마다 전부 잰 사람 · 하나 빠진 사람 · 035/037 하나만 잰 사람 · 3등급 신체조성 조합 · 경계값을 넣었다.
  - 예전 규칙: 항목마다 등급을 매겼다 — 처음에는 백분위 85/65/40(`V131`), 그다음 공식 기준표를 항목 하나씩(`V154`). 둘 다 한 사람에게 하나를 매기는 실제 인증과 달라 걷었다. 항목마다 굳혀 두던 `fitness_test_items.grade` 는 `V160` 으로 지웠다(값에서 다시 셈할 수 있는 칸이라 잰 값은 그대로다).
- `topPercentText` = `상위 ${max(1, 100 - percentile)}%` (백분위 24 → "상위 76%", 100 → "상위 1%" — 「상위 0%」 는 쓰지 않는다).

### 고정 문구 (`shared.domain.Copy`)
- 측정 disclaimer: `국민체력100 측정 데이터를 바탕으로 한 참고 정보입니다. 질병의 진단·치료를 위한 것이 아니며, 건강에 관한 판단은 전문가와 상담하세요.`
- band 문구: strength 「잘하고 있는 영역」 · steady 「꾸준히 하고 있는 영역」 · growth 「지금 키우기 좋은 영역」.

---

## 1. 인증·계정·가족 (identity)

`AuthResponse` = `{accessToken, refreshToken, userId, nextStep, profiles: ProfileSummary[], selfProfileId|null}`. `profiles` 는 이 계정에 붙은 프로필이라 0~1개이고, `selfProfileId` 는 그 프로필의 id 다.

`nextStep` 은 구글 로그인 · dev-login · review-login · 리프레시 · `/me` 가 같은 규칙으로 정한다(`NextStep.afterLogin`).

| 차례 | 조건 | `nextStep` |
|---|---|---|
| 1 | 프로필이 있고, 초대코드로 붙은 보호자(PARENT · `inviteStatus` CLAIMED)가 참여 방식을 아직 안 골랐다(`supportMode` null) | `SUPPORT_MODE` |
| 2 | 프로필이 있다 | `HOME` |
| 3 | 프로필 0개 · 코드 없음 | `CREATE_FAMILY` |
| 4 | 프로필 0개 · 코드 있음(로그인 요청의 `claimCode`) | `CLAIM` |

- 가족을 만든 보호자는 초대로 붙지 않았으므로(`NONE`) `supportMode` 가 null 이어도 `HOME` 이다.

### POST /api/v1/auth/google — 토큰 없이
요청 `{authorizationCode●, redirectUri●, claimCode?}`. 구글 인가코드 교환 → id_token 검증 → (provider=GOOGLE, providerUserId=sub) find-or-create. provider 는 google 하나로 고정, 계정 병합 경로 없음.
응답 200 `AuthResponse`.
- `nextStep` 은 위 표대로다. 로그인이 코드로 프로필을 붙이지는 않는다(붙이는 것은 `POST /profiles/claim`).
- 구글 인가코드 교환(외부 HTTP)은 DB 트랜잭션 밖에서 한다. 가입(find-or-create)과 토큰 발급만 한 트랜잭션이다.
- 로그인마다 새 리프레시 토큰 묶음(family)을 연다. 한 기기의 로그아웃 · 재사용 감지가 다른 기기를 끊지 않는다.
오류: 401 `GOOGLE_AUTH_FAILED`.

### POST /api/v1/auth/refresh — 토큰 없이
요청 `{refreshToken●}`. 응답 200 `AuthResponse`(새 액세스 · 리프레시 토큰).
- 회전(RFC 9700 §4.14.2): 보낸 토큰은 그 자리에서 폐기된다. 응답의 새 `refreshToken` 을 반드시 저장한다.
- 폐기된 토큰이 다시 오면 같은 묶음의 토큰을 모두 폐기하고 401 이다. 같은 토큰으로 두 요청이 동시에 오면 하나만 성공한다(조건부 UPDATE).
- 계정이 ACTIVE 가 아니면 묶음을 폐기하고 401 이다.
오류: 401 `INVALID_REFRESH_TOKEN`.

### POST /api/v1/auth/logout — 토큰 없이
요청 `{refreshToken?}` — 본문을 빼도 된다. 응답은 늘 204 다(모르는 토큰 · 빈 토큰도 204).
그 토큰이 속한 묶음을 폐기한다. 액세스 토큰은 건드리지 않는다(만료까지 산다).

### POST /api/v1/auth/dev-login — 토큰 없이 · local · compose · test 에서만
`app.auth.dev-login.enabled=true` 일 때만 빈이 등록된다. 운영(prod)에는 이 경로가 없다(404).
요청 `{providerUserId●(≤191), email?(≤255), claimCode?}`. 응답 200 `AuthResponse`(구글 없이 같은 흐름).
시드 데모 계정 `demo-parent`(가족 데모네 · nextStep HOME) · `demo-parent-2`(프로필 없음, 초대코드 `K7M2QT` 로 claim 가능).
같은 `providerUserId` 면 같은 계정이다. 딱 `demo-fresh` 일 때만 부를 때마다 **새 계정**(`demo-fresh-` + 무작위 8자, 프로필 없음 · nextStep `CREATE_FAMILY`)을 만든다 — FE 로그인 화면의 「새 계정 · 가족 없음」 단추가 보내는 값이라, 서버를 다시 띄우지 않고도 가족 만들기부터 몇 번이고 다시 볼 수 있다.
local · compose 의 자동 로그인은 `X-Dev-User-Id: <userId>` 헤더를 보낸 요청만 그 계정으로 인증한다(curl · 스크립트용). 헤더가 없거나 비어 있으면 401, UUID 가 아니면 400.

### POST /api/v1/auth/review-login — 토큰 없이 · 심사용 계정
`app.auth.review-login.enabled=true` 일 때만 빈이 등록된다. local · compose 는 켜 두고, prod 는 `APP_AUTH_REVIEW_LOGIN_ENABLED`(기본 `true`)로 켜고 끈다. 꺼져 있으면 경로가 없어 404 `NOT_FOUND` 다. test 프로필은 꺼 둔다.
심사위원이 구글 계정 없이 운영 서버에서 둘러보는 길이다(FE 로그인 화면 구글 단추 밑 「심사용 계정으로 둘러보기」). dev-login 과 달리 개발용 기능이 아니라 `DevFeatureGuard` 가 막지 않는다.
요청 본문 없음. 응답 200 `AuthResponse` — dev-login 과 같은 모양이고 `nextStep` 은 늘 `HOME`, `profiles` 는 이 계정의 보호자 프로필(엄마) 하나다.
- 부를 때마다 **새 계정과 새 체험 가족**을 만든다. 심사위원끼리 서로의 기록을 건드리지 않게 하려는 것이다. 계정은 provider `REVIEW`, providerUserId `review-` + 무작위라 같은 계정으로 다시 들어오는 길은 없다(그 브라우저의 리프레시 토큰으로만 이어 본다).
- 체험 가족 「체험 가족」: 엄마(이 계정 · 보호자 · 만 38세 여 · 참여 방식 `FULL`), 아빠(보호자 · 만 40세 남 · 계정 없음), 하윤(아이 · 만 11세 여 · 유소년 · 계정 없음 · 보호자 동의 있음), 서준(아이 · 만 6세 남 · 유아기 · 계정 없음 · 보호자 동의 있음).
  - 하윤은 사흘 전 날짜로 유소년 종목 일곱 가지(009 · 012 · 020 · 022 · 028 · 043 · 044)와 키 148cm · 몸무게 40kg · 허리둘레 62cm 를 재 뒀다. 인증 등급 `2등급`(`GRADED`)이 나온다.
  - 서준은 사흘 전 날짜로 유아기 종목 네 가지(009 · 012 · 022 · 050)와 키 · 몸무게를 재 뒀다. 일곱 가지를 다 재지 않아 등급 대신 `NEEDS_ITEMS` 다.
  - 네 사람 모두 운동할 수 있는 시간이 적혀 있다. 미션은 없다 — 들어와서 오늘 편성을 직접 짜 보게.
  - 가족 · 구성원 · 측정은 화면이 부르는 서비스를 그대로 거친다(나이 · 동의 · 항목 · 값 범위 규칙이 똑같이 걸린다). 한 트랜잭션이라 중간에 실패하면 계정도 남지 않는다.
- 같은 IP(IPv6 는 앞 64비트 대역)에서 한 시간에 30번, 또는 IP 와 상관없이 모두 합쳐 한 시간에 300번을 넘기면 계정을 만들기 전에 429 `TOO_MANY` 다. 통과한 요청만 세고, 셈은 서버 메모리에 둔다(서버 한 대 기준).
  IP 는 서블릿 `remoteAddr` 다. 브라우저 요청은 늘 Next 서버를 거쳐 오므로 prod 는 `server.forward-headers-strategy=native` 로 믿을 프록시(Tomcat 기본: 루프백 · 사설망, 바꾸려면 `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`)가 붙인 `X-Forwarded-For` 에서 브라우저 IP 를 꺼낸다. Next 는 이 헤더를 스스로 붙이지 않으므로 Next 앞의 HTTPS 프록시가 붙여야 한다(backend/README.md 「프로필」).
오류: 429 `TOO_MANY`.

### GET · POST /api/v1/dev/clock — 로그인 · local · compose 에서만
`app.dev.time-travel.enabled=true` 일 때만 빈이 등록된다. 운영(prod)에는 이 경로가 없다(404). 켜면 서버 시계(`Clock` 빈)가 앞으로 옮길 수 있는 시계로 바뀐다.
- `GET` → `{now, today, offset}` — 지금 서버 시각, 오늘(KST), 실제 시각에서 옮긴 폭(ISO 기간).
- `POST {"by":"P1D"}` 또는 `POST {"to":"2026-10-01T07:31:00+09:00"}` — 둘 가운데 하나만(아니면 400 `TIME_TRAVEL_TARGET`). 뒤로는 못 간다(409 `TIME_TRAVEL_BACKWARD`), 한 번에 400일까지(400 `TIME_TRAVEL_TOO_FAR`).
  옮기는 동안 건너뛴 정해진 일(「시각 · 날짜와 스케줄러」 표)을 원래 시각 차례대로, 그때마다 시계를 그 시각에 맞춰 돌린다. 간격 작업(멈춘 편성 정리)은 도착한 뒤 한 번.
  응답 `{from, now, today, offset, ran:[{task, runs, failures, first, last}]}` — 작업마다 한 줄.
- 처음으로 돌아가려면 서버를 다시 띄운다(H2 인메모리). 브라우저의 시계는 옮기지 않는다 — 화면이 기기 날짜로 「오늘」 을 셈하면 서버와 어긋난다.

### GET /api/v1/me — 로그인
응답 200 `{userId, nextStep, profiles, selfProfileId|null}`. `nextStep` 은 1장 머리의 표대로다. 초대로 들어온 보호자가 참여 방식을 고르기 전에 앱을 닫았다 다시 열면 `SUPPORT_MODE` 다.

### POST /api/v1/families — 로그인
요청 `{familyName●(1~20자), owner●: {name●(1~20), birthDate●, sex●}}`. `role` 없음 — 만든 사람은 항상 PARENT·owner.
응답 201 `{familyId, familyName, ownerProfile: ProfileSummary}`.
판정 차례: 400(몸통, `owner` · `owner.birthDate` · `owner.sex` 누락 포함) → 409 `ALREADY_IN_FAMILY`(이 계정에 이미 프로필이 붙어 있다) → 400(생년월일이 미래) → 422 `UNDER_14_NOT_ALLOWED`(만 14세 미만).
불변식: 가족에 PARENT 최소 1명. 가족 · 프로필 두 INSERT 는 한 트랜잭션. `profiles.user_id` 유니크(`V143`)라 동시에 두 번 눌러도 하나만 된다.

### POST /api/v1/families/{familyId}/profiles — 보호자
요청 `{name●(1~20), birthDate●, sex●, role●, heightCm?(30~230), weightKg?(5~250), guardianConsent?: {personalData●, healthData●}}`.
응답 201 ProfileSummary(`hasAccount:false`).
- 만 14세 미만이면 `guardianConsent` 가 있고 둘 다 true 여야 저장한다. 서버가 동의를 자동으로 찍지 않는다. `consent_*_at`·`consent_by` 는 서버가 채우고, 동의 이력(`consent_events`)에 한 줄 남긴다.
- `heightCm` · `weightKg` 는 프로필에 저장하고 응답에는 싣지 않는다. 안 적었으면 `null` 을 보내거나 칸을 뺀다(`0` 은 범위 밖이라 400).
- 만 4세 미만도 프로필은 만든다(측정만 불가). 역할은 생성 때 확정.
판정 차례: 400(몸통) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 400(생년월일이 미래) → 422 `UNDER_14_NOT_ALLOWED`(만 14세 미만을 PARENT 로) → 422 `UNDER_14_NOT_ALLOWED`(동의를 적는 보호자 자신이 만 14세 미만) → 422 `CONSENT_REQUIRED`(만 14세 미만인데 동의가 없거나 하나라도 false) → 409 `CONFLICT`(겹친 쓰기).

### GET /api/v1/families/{familyId}/profiles — 같은 가족
응답 200 `{familyId, familyName, profiles: ProfileSummary[]}`.
오류: 404 `FAMILY_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### PATCH /api/v1/profiles/{profileId} — 보호자(계정 없는 프로필 · 자기 프로필)
요청 `{name?(1~20, 공백만은 안 됨), birthDate?, sex?}` — 빠진 칸은 그대로 둔다. 응답 200 ProfileSummary(고친 뒤 값).
- 아이 생일을 고쳐 만 14세 미만이 되면 `consentRequired` 가 바로 true 가 되고, 동의 기록이 없으면 `consentGiven` · `measurable` 이 false 가 된다.
- 지난 측정의 나이 · 백분위는 굳힌 채 둔다.
판정 차례: 400(몸통) → 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 403 `FORBIDDEN`(계정이 붙은 다른 사람의 프로필) → 400(생년월일이 미래) → 422 `UNDER_14_NOT_ALLOWED`(PARENT 생일을 만 14세 미만으로) → 409 `CONFLICT`.

### PATCH /api/v1/profiles/{profileId}/support-mode — 자기 프로필(PARENT)
요청 `{supportMode●}`. 응답 200 ProfileSummary.
판정 차례: 400(`{}` · `{"supportMode": null}`) → 404 `PROFILE_NOT_FOUND` → 403 `FORBIDDEN`(내 계정에 붙은 프로필이 아님) → 422 `NOT_APPLICABLE`(CHILD 프로필) → 409 `CONFLICT`.

### PATCH /api/v1/profiles/{profileId}/consent — 보호자(자기 프로필 제외)
요청 `{personalData●, healthData●}`. 응답 200 `{consentGiven, consentAt|null, consentBy|null, measurable}`.
- 보호자 동의는 아이(CHILD) 프로필에만 있다. 대상이 보호자(PARENT)면 422 `CONSENT_NOT_APPLICABLE` 이다. 다른 보호자의 동의를 거두면 그 보호자의 칸 끝 · 측정이 막히는데 본인은 되돌릴 수 없어서다.
- 이미 거둔 채였던 보호자 동의는 `V151` 이 풀었다. 이력은 지우지 않고 `VOIDED` 줄(누가 · 무엇을은 null)을 더했다.
- 둘 중 하나라도 false 면 동의를 거둔 것이다. 거둔 뒤에는 0장 공통 규칙대로 새 기록이 422 `CONSENT_REQUIRED` 다. 지난 기록은 지우지 않는다(▲ 법적 규칙 미결).
- 거둔 동의는 만 14세가 지나도 막힌 채다. 보호자가 다시 동의해야 풀린다(만 14세 이상 본인 동의 절차는 없다).
- 부여 · 철회마다 `consent_events` 에 한 줄(누가 · 언제 · 무엇을) 남긴다. `kind` 는 `GRANTED` · `REVOKED` · `VOIDED`(앞선 철회를 무효로 함 — 마이그레이션만 쓴다)다. 다시 동의해도 철회 줄은 지우지 않는다. 이력을 보는 주소는 없다.
판정 차례: 400 → 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 403 `SELF_CONSENT`(자기 프로필) → 422 `CONSENT_NOT_APPLICABLE`(대상이 PARENT) → 422 `UNDER_14_NOT_ALLOWED`(부른 보호자가 만 14세 미만) → 409 `CONFLICT`.

### POST /api/v1/profiles/{profileId}/invite — 보호자
본문 없음. 응답 201 `{claimCode(6자리, 0/O·1/I 제외 대문자+숫자), expiresAt(+7일), shareUrl("{app.frontend-base-url}/claim?code=XXXXXX")}`.
- 살아 있는 코드(만료 전 · 안 씀)가 있으면 새로 만들지 않고 그 코드와 만료 시각을 그대로 준다(이때도 201). 없을 때만 새로 만들고, 발급한 보호자를 남긴다(`V143`).
- local · compose 의 `app.frontend-base-url` 기본값은 FE 개발 서버 `http://localhost:3000` 이다. prod 는 기본값이 없어 `APP_FRONTEND_BASE_URL` 을 빠뜨리면 기동이 멈춘다(localhost 링크가 나가지 않게).
판정 차례: 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 409 `ALREADY_CLAIMED`(이미 계정이 붙은 프로필) → 409 `CONFLICT`.

### GET /api/v1/invites/{claimCode} — 로그인
초대코드 미리 보기. 응답 200 `{familyName, profileName, role, ageGroup, invitedByName|null, expiresAt}`. `invitedByName` 은 코드를 발급한 보호자 이름이고, 발급자를 남기기 전에 만든 코드면 null.
코드는 대소문자를 가리지 않는다.
판정 차례: 429 `TOO_MANY` → 404 `CODE_NOT_FOUND` → 409 `ALREADY_CLAIMED` → 410 `CODE_EXPIRED`.

### POST /api/v1/profiles/claim — 로그인
요청 `{claimCode●(≤20)}`(대소문자 무시). 응답 200 `{profileId, familyId, role, nextStep}` — PARENT 면 `SUPPORT_MODE`, CHILD 면 `HOME`.
판정 차례: 400 → 429 `TOO_MANY` → 404 `CODE_NOT_FOUND` → 409 `ALREADY_CLAIMED` → 410 `CODE_EXPIRED` → 409 `ALREADY_MEMBER`(내가 이미 이 가족 구성원) → 409 `ALREADY_IN_FAMILY`(다른 가족에 프로필이 있음 — 한 계정 한 가족).
- 동시성: `UPDATE profiles SET user_id=? ... WHERE id=? AND user_id IS NULL` 조건부 UPDATE 한 문장. 영향 0행이면 `ALREADY_CLAIMED`. 사전 검사를 함께 지나친 두 가족 합류는 `profiles.user_id` 유니크가 막고 `ALREADY_IN_FAMILY` 로 바뀐다.
- 시도 제한: 미리 보기와 claim 이 없는 코드(형식이 틀린 코드 포함)를 받을 때마다 계정마다 센다. 10분 안에 10번을 넘으면 다음 요청은 코드를 찾기 전에 429 `TOO_MANY` 다. 셈은 서버 메모리에 둔다(서버 한 대 기준).

### GET /api/v1/profiles/{profileId}/availability — 같은 가족
운동할 수 있는 시간. 응답 200 `{profileId, slots: [{day, start, minutes}]}` — `day` 는 `MON`..`SUN`, `start` 는 `"HH:mm"`(KST), 요일 차례. 적어 둔 것이 없으면 `slots: []`(404 가 아니다).
오류: 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.
편성의 기본 분 · 운동을 막는 데에는 아직 쓰지 않는다(FE 가 편성 `minutes` 를 직접 보낸다). 다른 모듈에는 `identity.api.AvailabilityQuery` 로 연다.

### PUT /api/v1/profiles/{profileId}/availability — 보호자
요청 `{slots●: [{day, start, minutes}]}` — 한 주를 통째로 바꾼다(한 트랜잭션). 빈 목록이면 적어 둔 시간을 모두 지운다. 응답 200 GET 과 같은 모양.
판정 차례: 400 `BAD_REQUEST`(`slots` 없음) → 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 400 `INVALID_SLOT`.
`INVALID_SLOT`: 칸이 null · 요일이 `MON`..`SUN` 대문자가 아님 · 시각이 `HH:mm`(00:00~23:59)이 아님 · 분이 5~120 정수가 아님(소수도 거절) · 같은 요일이 두 번.

### POST /api/v1/families/{familyId}/cheers — 같은 가족(보내는 프로필은 대신)
요청 `{fromProfileId●, toProfileId●, kind?, message?(≤100자), stickerId?(≤20자), emoji?(≤20자), missionId?, replyToCheerId?}` — `message` · 스티커 중 최소 하나.
- `emoji` 는 `stickerId` 의 옛 이름이다. 전환 기간에만 받고, 둘 다 오면 `stickerId` 를 쓴다. 스티커 코드 목록은 서버가 검사하지 않는다.
- `fromProfileId` 는 「대신」 규칙을 지나야 한다: (가) 호출한 계정에 붙은 프로필, (나) 호출한 계정이 이 가족 보호자이고 `fromProfileId` 가 **계정 없는** 아이 프로필(부모 기기를 아이가 빌려 「알리기」 · 「고마워요」 를 보낸다).
- `kind` 를 안 보내면 서버가 정한다: 보내는 쪽이 PARENT 거나 받는 쪽이 CHILD 면 `PRAISE`, 아이 → 부모는 스티커가 있으면 `THANKS` · 없으면 `DONE`.
- 방향: `PRAISE` 는 부모 → 아이, `DONE` · `THANKS` 는 아이 → 부모만.
- 고마워요(`THANKS`): 스티커가 있어야 한다. `kind` 를 밝혀 보내면 `replyToCheerId` 도 있어야 한다. `replyToCheerId` 는 `THANKS` 에만 보낸다. 답할 대상은 스티커가 붙은 `PRAISE` 이고, 그 칭찬을 받은 사람이 보낸 사람에게 한 번만 보낸다(유니크 인덱스).
- `missionId` 는 이 가족의 미션인지만 본다. 보내는 · 받는 사람이 그 미션 참여자인지는 보지 않는다.
응답 201 `{cheerId, fromProfileId, toProfileId, kind, message, stickerId, emoji(= stickerId), missionId, replyToCheerId, createdAt}`.
판정 차례: 400(몸통) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `FORBIDDEN`(대신할 수 없는 `fromProfileId`) → 422 `SELF_CHEER` → 422 `NOT_FAMILY_MEMBER`(`toProfileId`) → 400 `BAD_REQUEST`(`THANKS` 모양) → 403 `NOT_A_PARENT`(아이가 `PRAISE`) → 422 `CHEER_KIND_NOT_ALLOWED`(방향) → 404 `CHEER_NOT_FOUND` → 422 `NOT_A_REPLY_TARGET` → 409 `ALREADY_THANKED` → 404 `MISSION_NOT_FOUND` → 429 `TOO_MANY`((보낸, 받는) 쌍 기준 분당 5회 초과).
저장 뒤 `CheerSent` 를 낸다. 경험치(스티커 붙은 `PRAISE` +10, 같은 미션에 한 번)는 같은 트랜잭션에서, 알림은 커밋 뒤 알림 전용 스레드에서 적는다(7장).
- 두 보호자가 같은 아이 · 같은 미션에 동시에 스티커를 붙여도 둘 다 201 이다. 경험치 · 첫 스티커 업적은 한 번만 쌓인다(원장 · 업적 넣기가 `ON CONFLICT DO NOTHING`).
Cheer 는 별도 애그리게잇. JPA 엔티티 그대로 써도 됨.

### GET /api/v1/families/{familyId}/cheers?toProfileId=&fromProfileId=&missionId=&size= — 같은 가족
받은 응원 목록. 응답 200 `{cheers: [{cheerId, fromProfileId, fromName, toProfileId, kind, message, stickerId, missionId, replyToCheerId, createdAt}]}` — 최신순.
- 거르기 값은 모두 선택이다. 다른 가족 프로필이면 빈 목록이다. 「벌써 알렸나 · 칭찬했나」 는 `missionId` 로 좁혀 읽는다.
- `size` 기본 20, 1~100 밖이면 400.
오류: 404 `FAMILY_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

---

## 2. 측정 (fitness)

부모만 볼 값: 호출 계정의 그 가족 프로필이 CHILD 면 측정 응답에서 인증 등급(`certification`) · 요인별 백분위 · 가장 낮은/높은 항목 · 코치 방향 · 체중 · 체지방률 · 허리둘레 · 「상위 n%」 문구를 비운다(null). `overallPercentile` 과 키 · 잰 값은 남긴다. 부모 계정은 아이 모드여도 다 받는다(서버가 화면을 모른다).

### GET /api/v1/fitness/items?ageGroup=&profileId=&testedOn=&sex= — 로그인(`profileId` 를 주면 보호자)
응답 200 `{ageGroup, items: [{itemCode, itemName, itemLabel, unit, factor, higherIsBetter, inputGroup, optional, equipment|null, range:{min,max}}]}`.
- `profileId` 가 있으면 그 프로필의 `testedOn`(없으면 오늘 KST) 기준 만 나이로 연령대를 정한다. 등록 검사와 같은 셈이라 생일 직후 지난 결과지를 옮겨 적어도 어긋나지 않는다. 이때 `ageGroup` 은 보지 않는다.
  권한 · 날짜는 등록과 같다: 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 400(미래 날짜) → 422 `NOT_MEASURABLE`(만 4세 미만).
- 없으면 `ageGroup`(한글 라벨) 으로 준다. 둘 다 없거나 `testedOn` 만 오면 400.
- `sex` 는 `M` · `F` 인지만 검사하고 항목을 바꾸지 않는다.

### POST /api/v1/profiles/{profileId}/fitness-tests — 보호자
자녀 계정은 자기 것도 403 `NOT_A_PARENT` 다(FE 는 측정을 보호자 화면에만 둔다).
요청 `{testedOn●, source●, heightCm?(30~230), weightKg?(5~250), bodyFatPct?(3~60), waistCm?(30~200), items●[{itemCode●, value●}]}`.
- `bodyFatPct` 는 체지방률 %, `waistCm` 은 허리둘레 cm 다(둘 다 소수 한 자리까지 저장). 키 · 몸무게처럼 고를 수 있고, 범위 밖이면 400 `BAD_REQUEST`, 안 적었으면 `null` 을 보내거나 칸을 뺀다.
응답 201 `{fitnessTestId, testedOn, bodyFatPct|null, waistCm|null, items:[{itemCode, itemLabel, unit, value, percentile|null, band|null, topPercentText|null}], weakest|null, strongest|null, certification, disclaimer}`.
- `certification`: `{grade|null, status, missingItems:[{itemCodes, label}], peers:[{grade, ratio}]}`(OpenAPI 스키마 `Certification` · `MissingItem` · `PeerGrade`) — 이 회차의 인증 등급(한 사람에게 하나, 위 「백분위·등급 계산」). 등록은 보호자만 해서 늘 있다. 항목 줄에는 등급이 없다.
  예: 여 만 11세 · 012 4.0 · 020 70 · 028 41.3 · 키 145 · 몸무게 38 → 1 · 2등급은 009 · 043 · 022 · 044, 3등급은 009 · 허리둘레가 없어 판정하지 못한다.
  ```json
  "certification": {"grade": null, "status": "NEEDS_ITEMS",
    "missingItems": [{"itemCodes": ["009"], "label": "윗몸말아올리기"}, {"itemCodes": ["042"], "label": "허리둘레"}],
    "peers": [{"grade": "1등급", "ratio": 0.0322}, {"grade": "2등급", "ratio": 0.0983},
              {"grade": "3등급", "ratio": 0.2282}, {"grade": "참가", "ratio": 0.6413}]}
  ```
  여기에 009 20회 · 허리둘레 60cm 를 더하면(BMI 18.07 · 허리둘레-신장비 0.414) `"grade": "3등급"`, `"status": "GRADED"` 이고 `missingItems` 는 1등급에 모자란 022 · 043 · 044 다.
  일곱 종목을 다 재면(020 55 · 028 45.0 · 009 40 · 012 12.0 · 043 33 · 022 170 · 044 20 · 키 145 · 몸무게 38 · 허리둘레 60) 1등급을 판정하고 020 이 62 에 못 미쳐 `"grade": "2등급"`, `"status": "GRADED"`, `"missingItems": []` 다.
판정 차례: 400(몸통) → 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 400(`testedOn` 이 미래) → 422 `NOT_MEASURABLE`(측정일 기준 만 4세 미만) → 422 `CONSENT_REQUIRED` → 409 `DUPLICATE_DATE` → 400 `NO_ITEMS` → 항목마다 400 `ITEM_NOT_ALLOWED`(005/006) · 400 `UNKNOWN_ITEM` → 400(같은 항목 두 번) → 422 `ITEM_NOT_FOR_AGE_GROUP` → 400 `ITEM_OUT_OF_RANGE`.
불변식: 항목 0개면 저장 안 함. 백분위는 저장 시점에 굳음(인증 등급은 저장하지 않고 응답 때 셈한다). 한 프로필 같은 날짜 측정은 하나. `FitnessTest` 통째로 저장. age_at_test = testedOn 기준 만 나이. 동의 판정은 오늘 기준이다.
저장 뒤 `FitnessTestRegistered` 를 낸다. 가장 이른 회차를 뺀 회차(다시 잰 회차)가 새로 생기면 경험치 `REMEASURE` +20 을 같은 트랜잭션에서 적는다. 커밋 뒤에는 알림함이 그 아이의 지난 회차로 만든 `REMEASURE` 알림을 지운다(7장).

### GET /api/v1/profiles/{profileId}/fitness-tests?size= — 같은 가족
측정 이력. 응답 200 `{tests:[{fitnessTestId, testedOn, overallPercentile|null, heightCm|null, weightKg|null}]}` — testedOn 이 늦은 회차부터. 이력이 없으면 빈 목록.
`size` 기본 20, 1~100 밖이면 400. `overallPercentile` 은 `fitness-map` 의 것과 같은 셈(항목 백분위 평균, 백분위가 나온 항목이 없으면 null). 키 · 몸무게는 그 회차에 같이 적은 값이다. 아이 계정이면 `weightKg` 는 늘 null.
오류: 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### GET /api/v1/profiles/{profileId}/fitness-tests/latest — 같은 가족
응답 200 `{fitnessTestId, testedOn, heightCm|null, weightKg|null, bodyFatPct|null, waistCm|null, radar, items, weakest|null, strongest|null, coachDirection|null, certification|null, disclaimer}`.
이력이 없어도 **404 가 아니라 200** 이다: `fitnessTestId · testedOn · heightCm · weightKg · bodyFatPct · waistCm` 은 null, `radar` 6요인 percentile null, `items:[]`, `certification` 은 null, `coachDirection` 은 부모 계정이면 `"GROWTH"` · 아이 계정이면 null.
- `heightCm` · `weightKg` · `bodyFatPct` · `waistCm`: 그 회차에 같이 적은 값만. 없으면 null 이고, 프로필(가입 때) 값으로 채우지 않는다.
- `radar`: 근력 · 근지구력 · 유연성 · 심폐지구력 · 순발력 · 민첩성 차례의 `{factor, percentile|null}` 6개(요인에 항목 여럿이면 평균). 협응력 · 평형성은 싣지 않는다.
- `items[]`: `{itemCode, itemLabel, unit, value, percentile, band, topPercentText}` — 항목마다 등급은 없다. `certification`: 등록 응답과 같은 모양. `weakest/strongest`: `{factor, itemCode, percentile}` — 레이더 밖 요인(협응력: 017 · 044 · 051)도 될 수 있다. `coachDirection`: weakest 백분위 > 75 → `STRENGTHEN`, 아니면 `GROWTH`.
- 아이 계정이면 `weightKg` · `bodyFatPct` · `waistCm` · `radar[].percentile` · 항목의 `percentile · band · topPercentText` · `weakest` · `strongest` · `coachDirection` · `certification` 이 null 이다.
- 「가장 최근」 은 testedOn 이 가장 늦은 회차다. 지난 날짜로 측정을 적으면 방금 적은 회차가 아니라 더 늦은 회차가 나온다.
오류: 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### GET /api/v1/families/{familyId}/fitness-map — 같은 가족 (피그잼 F0 `/home` 가족 체력 지도 ★메인)
홈 화면 한 번의 조회. 응답 200 `{familyId, familyName, members:[{profileId, name, role, ageGroup, sex, hasAccount, supportMode, measurable, consentRequired, consentGiven, headline|null, latest|null:{fitnessTestId, testedOn, overallPercentile|null(항목 백분위 평균), weakest, strongest, coachDirection}}], disclaimer}`.
- `headline` 예: 「유소년 상위 49%」. 연령대는 **측정 당시** 연령대다. 그래서 오늘 연령대인 `ageGroup` 과 다를 수 있다(11세에 재고 지금 청소년이면 「유소년 …」).
- 아이 계정이면 `headline` · `latest.weakest` · `latest.strongest` · `latest.coachDirection` 이 null 이고 `overallPercentile` 은 남는다.
- `latest=null` 이면 "첫 측정을 등록하면 지도가 그려져요", `measurable=false` 면 측정 버튼을 띄우지 않는다. 구성원 사이 순위·비교는 내보내지 않는다.
오류: 404 `FAMILY_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

---

## 3. 활동 · 쉬는 날 (activity)

`activity_daily(profile_id, activity_date, source, steps, active_minutes, active_seconds)` unique(profile_id, activity_date, source).
- 시간은 초로 쌓는다(`V144`, 옛 행은 분 × 60). 분은 초 합에서 한 번 내림한다. `active_minutes` 는 호환용으로 같이 적는다.
- 「움직인 날」 = 그날 TIMER · VIDEO 초 > 0. 이어서 한 날 · 리그의 해낸 날 · 쉬는 날 `ALREADY_MOVED` · 캘린더 `minutes` 가 같은 기준을 쓴다.
- 공개 API(`activity.api`): `ActivityRecorder`(steps 덮어쓰기 MANUAL / 초 · 분 누적 TIMER · VIDEO), `ActivityQuery`(기간 합계 · 움직인 날), `RestDayQuery`(쉬는 날).
- 활동을 쌓는 웹 경로는 coaching 에 있다(칸 끝 · 타이머 · 걸음수 · 영상 진행). 동의 판정은 coaching 이 기록 전에 한다.

쉬는 날 카드: 가족 단위로 한 달 두 장. 세 경로 모두 그달 카드 모양 `{month, perMonth: 2, left, days: ["YYYY-MM-DD"]}` 으로 답한다.

### GET /api/v1/families/{familyId}/rest-cards?month=YYYY-MM — 같은 가족
전환기 별칭 `GET /families/{familyId}/rest-days` 도 같다(deprecated).
응답 200 그달 카드. `month` 가 없으면 이번 달(KST), 형식이 틀리면 400.
오류: 404 `FAMILY_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### POST /api/v1/families/{familyId}/rest-cards — 보호자
전환기 별칭 `POST /families/{familyId}/rest-days` 도 같다(deprecated).
요청 `{date}`(설계안 이름 `restDate` 도 받는다). 응답 201 그달 카드.
판정 차례: 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 422 `INVALID_DATE`(날짜 없음 · 형식 틀림 · 오늘부터 이번 달 끝 밖 — 400 이 아니다) → 409 `ALREADY_REST_DAY` → 409 `NO_REST_CARD_LEFT` → 422 `ALREADY_MOVED`(그날 아이 누구든 이미 움직였다) → 409 `CONFLICT`.
두 보호자가 동시에 쓰면 `(family_id, rest_month, card_no)` 유니크가 막고, 늦은 쪽은 다시 읽어 정확한 코드로 답한다(세 번까지).

### DELETE /api/v1/families/{familyId}/rest-cards/{restDate} — 보호자
전환기 별칭 `DELETE /families/{familyId}/rest-days/{restDate}` 도 같다(deprecated).
쉬는 날을 되돌리고 카드를 돌려준다. 응답 200 그달 카드.
판정 차례: 400(날짜 형식) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 422 `INVALID_DATE`(지난 날) → 404 `NOT_REST_DAY`.
그날 이미 운동했는지는 보지 않는다.

---

## 4. 코치·미션·영상 (coaching)

### POST /api/v1/families/{familyId}/coach/runs — 보호자
한 번의 편성 = **아이 한 명의 하루**(FE 요청서 1장 ②). triggerType 은 `MANUAL`. 자동 편성은 없다.
요청 `{profileId●, date●, minutes?(5~60), minutesPerSession?(5~60), quiet?, place?, focusFactor?, withParent?}`
- `minutes` 가 없을 때만 `minutesPerSession` 을 쓴다(옛 서버용으로 FE 가 같이 보낸다). 둘 다 없으면 400 — 기본 분을 서버가 고르지 않는다.
- `quiet` · `withParent` 가 없으면 false. `place` 는 `HOME` · `OUTDOOR` · 없음(장소를 가리지 않음). `focusFactor` 는 요인 이름(한글 또는 영문) 또는 null — null 이면 코치가 가장 낮은 요인을 고른다.
- 옛 칸 `weekStart` · `daysPerWeek` 는 받지 않는다(모르는 칸은 무시).
응답 202 `{coachRunId, status:"RUNNING", pollAfterMs:1500}`.
판정 차례: 400(몸통) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 422 `INVALID_DATE`(오늘 KST 보다 앞선 날짜) → 422 `NOT_FAMILY_MEMBER`(대상이 이 가족이 아님) → 422 `CONSENT_REQUIRED`(대상의 동의 없음) → 422 `NO_MEASURED_MEMBER`(대상이 측정 대상(만 4세 이상)인데 측정 기록이 없음. 만 4세 미만은 측정 없이 진행) → 409 `RUN_IN_PROGRESS` → 429 `TOO_MANY`(심사용 계정만, 하루(KST) 20번을 넘김).
잠금
- (대상, 날짜)에 RUNNING 이 있을 때만 409 `RUN_IN_PROGRESS`. `coach_runs.lock_key`(RUNNING 동안만 `profileId|date`) 유니크 인덱스라 동시에 들어온 두 요청도 하나만 통과한다.
- 심사용 계정(`POST /auth/review-login` 이 만든 계정)은 편성을 하루(KST)에 20번까지 시작한다. 21번째는 실행을 만들기 전에 429 `TOO_MANY` 다. 누구나 만들 수 있는 계정이 AI(LLM) 편성을 끝없이 돌리지 못하게 하려는 것이다. 통과한 요청만 세고 셈은 서버 메모리에 둔다. 구글 계정은 세지 않는다.
- 새 실행이 들어가면 같은 (대상, 날짜)의 `AWAITING_APPROVAL` 은 `REJECTED`(사유 「새 제안으로 바뀌었어요」)가 된다. `APPROVED` 뒤의 추가 편성(「AI 코치에게 더 받기」)은 막지 않는다.
- 만든 지 223초가 넘은 RUNNING 은 끝내지 못한 실행으로 보고 FAILED(`STALE`)로 바꿔 잠금을 푼다. 223초 = AI 시작 호출(연결 1s + 읽기 2s) + 40회 × (1.5s + 연결 1s + 읽기 3s). 기동 때 한 번, 그 뒤 223초마다 돈다(`StaleCoachRunSweeper`). 정리된 실행은 AI 결과가 늦게 와도 되살아나지 않는다.
비동기: 커밋 뒤 편성 전용 스레드 풀(`app.coach.executor.pool-size` 기본 8)에 넘긴다. 풀과 대기열이 다 차면 곧바로 FAILED(`BUSY`)다. 풀에서 `AiGateway.startCoachRun` → `getCoachRun` 1.5s 간격 최대 40회 폴링 → 결과 처리는 8장.
참여자: 편성 대상(`coachRole` 주행자)이고, `withParent` 면 편성을 요청한 보호자가 PARENT · `동반자`로 붙는다. AI 가 넣은 응원 부모 · 다른 구성원은 참여자에서 뺀다. 대체 편성 · 스텁도 하루짜리 미션 1건을 낸다.

### GET /api/v1/families/{familyId}/coach/runs/latest?profileId= — 같은 가족
가장 최근 실행(상태와 상관없이). `profileId` 가 있으면 그 프로필을 대상으로 짠 것, 없으면 가족 전체에서. 아이마다 `?profileId=` 를 붙이면 형제의 제안이 서로 가리지 않는다.
- `APPROVED` 인데 그 실행으로 만든 미션이 하나도 남지 않은 실행(보호자가 모두 지움)은 건너뛰고 그 앞 실행을 준다. 그 실행을 `GET /coach/runs/{runId}` 로 부르면 그대로 `APPROVED` · `missionCount` 0 이다.
응답 200 `CoachRunView`(아래와 같은 모양). 없으면 404 `COACH_RUN_NOT_FOUND`.
오류: 404 `FAMILY_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 404 `COACH_RUN_NOT_FOUND`.

### GET /api/v1/coach/runs/{runId} — 같은 가족
응답 200 `CoachRunView` = `{coachRunId, familyId, status, weekStart, profileId|null, date|null, summary|null, steps:[{seq,name,status,summary}], proposals|null, canApprove, missionCount, rejectedReason|null, failureCode|null, notices:[]}`.
- `profileId` · `date`: 누구의 어느 날을 짠 실행인지. 없앤 주간 편성이 남긴 옛 행은 둘 다 null. `weekStart` 는 그 날짜가 든 주의 월요일이다.
- `canApprove` = `AWAITING_APPROVAL` 이고, 호출자가 보호자이고, 만들 항목이 전부 지난 것은 아니고(전부 지났으면 승인이 409), 미션으로 옮길 참여자가 모두 보호자 동의가 있다(없으면 승인이 422 `CONSENT_REQUIRED`).
- `steps` 는 실행이 끝날 때 한 번에 저장된다(폴링 중에는 비어 있다).
- `failureCode`: FAILED 일 때만 있다. `NO_CITATIONS`(AI 가 근거가 없다고 거부) · `AI_FAILED`(AI 가 짜지 못했고 대체 편성할 근거도 없음) · `CONSENT_REQUIRED`(요청 뒤 대상의 동의를 거둠) · `BUSY`(편성 풀이 가득 참) · `STALE`(정리 작업이 끝냄) · `ERROR`(AI 400 · 409 · 서버가 제안을 저장하다 실패 등). AI 응답을 읽지 못한 것은 `ERROR` 가 아니라 대체 편성으로 간다(8장). 코드라서 화면 문구는 FE 가 정한다.
- `notices`: AI 가 제안과 함께 준 알림(예: 또래 자료가 없어 다른 연령대 자료도 골랐다). 늘 배열.
`proposals[]`: `{position, title, rationale|null, targetMetric, targetValue, startDate, endDate, participants:[{profileId, role, coachRole}], video|null:{videoId, title|null, url, startSec|null, badges[], mediaUrl|null, thumbnailUrl|null}, citations:[{index, label, chunkId, url|null}], sessions:[{position, phase, title, factor|null, minutes, clip|null:{videoId, startSec, endSec|null, title|null, mediaUrl|null, thumbnailUrl|null}}]}`.
- `sessions`: 제안의 칸. 모양은 미션 칸과 같고, 승인하면 그대로 미션 칸으로 복사된다. 칸 `minutes` 는 서버가 채운다(8장 칸 분 배분). 칸 끝의 `seq` 는 이 `position` 이다.
- `video`: 영상은 videoId 만 저장한다(`V134` 가 `exercise_videos` FK 를 걷었다). `exercise_videos` 에 있는 영상이면 제목 · 배지를 붙이고, 없으면 `title:null` · 유튜브 주소 · `badges:[]`.
- `mediaUrl` · `thumbnailUrl`(대표 영상 · 칸 `clip` 둘 다): 공단 영상이면 mp4 주소 · 첫 장면 이미지, 유튜브 영상이면 둘 다 null. 화면은 `mediaUrl` 이 있으면 `<video>` 로 `startSec` ~ `endSec` 를 틀고, 없으면 지금처럼 videoId 로 유튜브를 튼다. 공단 영상은 대표 영상의 `url` 도 mp4 주소다. 값은 저장하지 않고 조회 때 `exercise_videos` 에서 videoId 로 붙인다(아래 「운동 영상 · 구간 카탈로그」).
- `badges`: noise QUIET → 「조용함」, space SMALL_ROOM → 「좁은 공간 OK」, equipment null → 「준비물 없음」.
오류: 404 `COACH_RUN_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### POST /api/v1/coach/runs/{runId}/approve — 보호자 ★
본문 없음. 응답 200 `{coachRunId, status:"APPROVED", approvedBy(프로필 id), approvedAt, createdMissions:[{missionId, title, origin:"COACH"}]}`.
판정 차례: 404 `COACH_RUN_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 409 `ALREADY_APPROVED` · 409 `INVALID_STATE`(승인 대기가 아님) → 422 `CONSENT_REQUIRED`(복사할 참여자 중 동의 없는 사람) → 409 `PROPOSAL_EXPIRED`(항목의 기간이 전부 지남 — 실행은 승인 대기 그대로) → 409(조건부 UPDATE 가 0행, 다른 요청이 먼저 결정).
한 트랜잭션: 도메인 승인 → 동의 확인 → 기간 확인 → run 상태 전이(조건부 UPDATE) → 항목마다 missions INSERT(제안 복사, 칸 포함) → mission_participants INSERT.
- 참여자가 빈 항목 · 끝날이 오늘보다 앞인 항목은 미션으로 만들지 않고 건너뛴다.
- 만든 미션마다 `MissionCreated` 를 낸다. 미션이 만들어지는 길은 승인과 직접 만들기 둘뿐이다.
도메인 예외(CoachRunTest): `ParentRoleRequiredException`(NOT_A_PARENT), `CoachFamilyAccessDeniedException`(NOT_SAME_FAMILY), `CoachRunAlreadyDecidedException`(ALREADY_APPROVED 승인 후 / INVALID_STATE 그 밖), `CoachApprovalRequiredException`.

### POST /api/v1/coach/runs/{runId}/reject — 보호자
요청 `{reason?(≤300자)}`, 본문을 빼도 된다. 응답 200 `{coachRunId, status:"REJECTED", rejectedReason, missionCount:0}`.
판정 차례: 400 → 404 `COACH_RUN_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 409 `ALREADY_APPROVED` · `INVALID_STATE`.

### POST /api/v1/families/{familyId}/missions — 보호자
직접 만들기. 한 건 또는 여러 날(날마다 하루짜리 한 건).
요청 `{title●(1~50), startDate?, endDate?, dates?(1~28개), targetMetric●, targetValue●(≥1, 칸 없는 TIMER_MINUTES 는 1~360), videoId?, participantProfileIds●(1~5, 같은 가족), sessions?(≤10)}`.
- 칸 없는 `TIMER_MINUTES` 미션은 미션 전체가 한 칸이다. 칸 끝 한 번에 받는 재생 초 상한이 10800초이고 칸 시간의 절반 이상이어야 끝나므로, 360분을 넘으면 끝낼 수 없어 400 이다.
- 날짜는 둘 중 하나로 보낸다: `startDate` · `endDate`(한 건, `endDate ≥ startDate`) 또는 `dates`(같은 날은 한 번만, 이른 날부터). 둘 다거나 둘 다 없으면 400.
- 하나라도 틀리면 아무것도 만들지 않는다(한 트랜잭션).
`sessions[]`(칸): `{position●(1부터), phase●(SessionPhase), title●(≤120), factor?(요인 이름), minutes●(1~60), clip?:{videoId●, startSec●(≥0), endSec●, title?(≤120)}}`
- 보낸 `position` 차례 그대로 저장하고, 단계로 다시 줄 세우지 않는다. 같이 온 `completed` · `verifiedBy` 는 버린다(끝냈는지는 사람마다다).
- 칸이 있으면 `targetMetric` 은 `TIMER_MINUTES` 여야 하고 `targetValue` 는 칸 `minutes` 합과 같아야 한다. 서버가 값을 고쳐 넣지 않는다.
- 칸의 `clip.videoId` 는 카탈로그로 확인하지 않고 사본으로 저장한다(404 `VIDEO_NOT_FOUND` 가 나지 않는다). 클립 표를 다시 적재해도 지난 미션의 칸은 바뀌지 않는다.
- 칸 규칙을 어기면 400 `BAD_REQUEST` 다. 검사하는 단계가 둘이다.
  - 몸통 단계(권한 판정보다 먼저): 11칸 이상, position 이 1보다 작음, minutes 가 1~60 밖, `clip.videoId` 가 `[A-Za-z0-9_-]{1,32}` 가 아님, `endSec <= startSec`.
  - 미션을 만드는 단계(`VIDEO_NOT_FOUND` 뒤): position 이 1..n 이 아님(겹침 · 빠짐) → 칸이 있는데 `targetMetric` 이 `TIMER_MINUTES` 가 아님 → `targetValue` 가 칸 `minutes` 합과 다름 → 칸 없는 `TIMER_MINUTES` 인데 `targetValue` 가 360 초과. 이 차례로 검사한다.
응답 201 `{missionId, origin:"MANUAL", coachRunId:null, serverVerifiable, missions:[{missionId, startDate, endDate}]}` — 맨 위 칸은 첫 건 값이다.
판정 차례: 400(몸통 · 날짜 칸 조합 · `endDate < startDate` · 칸의 몸통 단계 규칙) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 422 `INVALID_DATE`(시작일이나 `dates` 의 어느 날이 오늘 KST 보다 앞) → 422 `NOT_FAMILY_MEMBER` → 422 `CONSENT_REQUIRED` → 404 `VIDEO_NOT_FOUND`(미션 단위 `videoId`) → 400(칸의 미션 단계 규칙).
만든 미션마다 `MissionCreated` 를 낸다. 멱등 키는 없다(응답을 잃고 다시 보내면 두 벌이 생길 수 있다).

### GET /api/v1/families/{familyId}/missions?scope=ALL|MINE|FAMILY&status=ACTIVE|DONE|EXPIRED — 같은 가족
응답 200 `{missions: MissionView[]}`.
`MissionView` = `{missionId, title, origin, coachRunId|null, targetMetric, targetValue, serverVerifiable, startDate, endDate, rationale|null, video|null:{videoId, title|null, url, durationSec|null, startSec|null, mediaUrl|null, thumbnailUrl|null}, participants:[{profileId, name, progress, completed, verifiedBy|null, needsGuardianCheck, doneSessions:[position]}], sessions:[{position, phase, title, factor|null, minutes, clip|null:{videoId, startSec, endSec|null, title|null, mediaUrl|null, thumbnailUrl|null}}]}`
- `sessions` 는 position 오름차순. 칸 없는 미션은 `[]`. 코치 미션은 제안의 칸을 복사해 든다.
- `doneSessions`: 그 사람이 끝낸 칸의 position, 오름차순.
- `clip.endSec` 가 null 이면 구간이 아니라 영상 한 편이다. 유튜브 AI 영상은 길이 자료가 없어 `video.durationSec` 가 null 이다(공단 영상은 길이가 있다).
- `mediaUrl` · `thumbnailUrl` 은 제안과 같다: 공단 영상이면 mp4 주소 · 첫 장면 이미지, 유튜브 영상이면 null. 직접 만든 미션의 칸도 videoId 로 붙는다.
- `progress` 는 소수 셋째 자리까지(0.3333… → 0.333).
- 정렬: startDate 내림차순, 같으면 만든 차례.
`MINE` = 내 계정의 프로필이 참여자. `FAMILY` = 참여자 2명 이상. `ACTIVE` = 오늘 ≤ endDate 이고 전원 완료 아님; `DONE` = 전원 완료; `EXPIRED` = endDate 지났고 미완료.
진행도(서버 계산, 0.0~1.0). 읽을 때 기간 안 미션의 미완료 참여자만 다시 계산해 바뀐 것만 저장한다. 기간이 끝난 미션 · 완료한 참여자는 저장된 값 그대로다.
- 칸 있는 미션: 그 사람이 끝낸 칸의 분 합 ÷ 전체 칸 분 합. 칸을 다 끝내면 완료, `verifiedBy` `VIDEO_PROGRESS`. 활동 합계는 보지 않는다.
- 칸 없는 미션을 칸 끝으로 position 1 까지 끝냈으면 완료(미션 전체를 한 칸으로 본다).
- 그 밖의 칸 없는 옛 미션: VIDEO_DONE = 완주(maxProgress≥0.9) 횟수/targetValue. TIMER_MINUTES = 기간 내 TIMER+VIDEO 분/targetValue. STEPS = 기간 내 MANUAL steps 합/targetValue — 도달해도 보호자 확인 전 completed=false, needsGuardianCheck=true.
오류: 400(scope · status 값) · 404 `FAMILY_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### GET /api/v1/missions/{missionId} — 같은 가족
응답 200 `MissionView`(목록 원소와 같은 모양 · 같은 다시 계산 규칙).
오류: 404 `MISSION_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### DELETE /api/v1/missions/{missionId} — 보호자
응답 204.
판정 차례: 404 `MISSION_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 409 `MISSION_ENDED`(endDate < 오늘 KST) → 409 `MISSION_ALREADY_STARTED`(누가 칸을 끝냈거나 완료된 참여자가 있음).
- 미션 행을 `SELECT … FOR UPDATE` 로 잠그고 읽는다. 칸 끝이 먼저 들어갔으면 지우기가 409, 지우기가 먼저면 칸 끝은 404 `MISSION_NOT_FOUND` 다.
- 느낌 · 참여자 · 칸 · 미션 행을 지운다. 응원(`cheers.mission_id`) · 경험치 원장은 FK 가 없어 missionId 를 든 채 남는다.
- 지운 뒤 `MissionCancelled` 를 낸다(알림함이 그 미션의 알림을 지운다).

### POST /api/v1/missions/{missionId}/sessions/{seq}/complete — 대신 + 참여자
전환기 별칭 `POST /missions/{missionId}/sessions/{seq}/done` 도 같다(deprecated · 지금 FE 가 부르는 이름).
운동 한 칸 끝. `seq` = 칸 `position`. 칸 없는 미션은 `seq` 1 을 미션 전체 한 칸(분 = targetValue, 단계 MAIN)으로 받는다.
요청 `{profileId●, activeSeconds●(0~10800, 영상 재생 초), startedAt●, endedAt●}`.
응답 200 `{position, verifiedBy:"VIDEO_PROGRESS", missionProgress, missionCompleted, xpGained}` — `xpGained` 는 부른 프로필 몫이다.
판정 차례: 400(몸통) → 404 `MISSION_NOT_FOUND`(잠금을 기다리는 사이 지워졌어도) → 404 `SESSION_NOT_FOUND` → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN`(그 프로필 이름으로 보낼 수 없음) → 403 `NOT_A_PARTICIPANT` → 422 `CONSENT_REQUIRED` → **이미 끝낸 칸이면 200 · `xpGained` 0**(아무것도 다시 쓰지 않는다) → 422 `MISSION_NOT_ACTIVE`(오늘이 기간 밖) → 400(`endedAt ≤ startedAt`) → 422 `TOO_SHORT`.
- 미션 행을 `SELECT … FOR UPDATE` 로 잠그고 읽는다(지우기 · 느낌과 같은 잠금). 그래서 같은 미션의 칸 끝은 차례로 돈다. 두 아이가 서로 다른 칸을 동시에 끝내도 늦은 쪽이 먼저 온 쪽의 기록을 보고 진행도 · 완료 · 경험치를 센다.
- 인정 초 = min(`activeSeconds`, `endedAt − startedAt`). 칸 시간(분 × 60)의 절반보다 짧으면 422 `TOO_SHORT`. 인정 초를 칸 시간으로 자르지는 않는다.
- 끝낸 날 = 서버가 받은 날(KST).
- 기록: `mission_session_completions`(`V144`, 기본 키 (미션, 칸, 프로필)) 한 행 · 활동 VIDEO 인정 초 · 진행도 다시 계산 · 경험치(칸 +5, 미션을 다 끝내면 +20)를 한 트랜잭션에서 한다.
- 아이가 끝낸 칸은 「같이 하기로 한 보호자」 에게도 한 행씩 적는다: 코치 미션은 `coachRole` 동반자, 직접 만든 미션은 PARENT 참여자 전원. 형제 · 응원만 하는 부모 · 동의 없는 보호자는 뺀다. 보호자가 끝낸 칸은 그 보호자 것뿐이다.
- 같은 칸 요청 둘이 동시에 오면 늦은 쪽은 먼저 온 쪽의 커밋을 기다렸다가 「이미 끝낸 칸」(200 · 0)으로 답한다. 그래도 유니크 위반이 나면(다른 미션의 칸 끝과 같은 날 활동 행을 동시에 처음 넣음) 한 번 더 부른다.
- 새로 적은 사람마다 `SessionCompleted` 를, 이 칸으로 미션이 막 끝났으면 `MissionCompleted` 도 낸다.

### POST /api/v1/missions/{missionId}/feedback — 대신 + 참여자
운동 느낌. 요청 `{profileId●, feel●(EASY|GOOD|HARD)}`. 응답 204. 다시 보내면 느낌 · 시각을 덮어쓴다(`mission_feedback`, `V147`).
판정 차례: 400 → 404 `MISSION_NOT_FOUND` → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN` → 403 `NOT_A_PARTICIPANT` → 422 `CONSENT_REQUIRED`.
저장만 한다. 다음 편성에 싣는 일은 AI 계약에 칸이 생긴 뒤다. 미션 행을 지우기와 같은 방식으로 잠근다(지운 뒤에 온 느낌은 404).

### POST /api/v1/missions/{missionId}/participants/{profileId}/confirm — 보호자 · FE 가 부르지 않음 · 걷을 후보
본문 없음. 응답 200 `{missionId, profileId, completed, verifiedBy, confirmedBy, verifiedAt}`.
- STEPS 미션: 목표에 도달했으면 `completed:true` · `verifiedBy:"SELF_REPORT"`. 도달 전이면 422 `TARGET_NOT_REACHED`.
- 서버가 재는 미션(타이머 · 영상 · 칸)은 확인할 것이 없어 바꾸지 않고 200 이다.
판정 차례: 404 `MISSION_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `NOT_A_PARENT` → 403 `NOT_A_PARTICIPANT` → 422 `TARGET_NOT_REACHED`.

### POST /api/v1/missions/{missionId}/activity/steps — 대신 + 참여자 · FE 가 부르지 않음 · 걷을 후보
요청 `{profileId●, activityDate●(미래 불가), steps●(0~100000, 그날 총량 덮어쓰기)}`.
응답 200 `{source:"MANUAL", serverVerified:false, verifiedBy:"SELF_REPORT", missionProgress, missionCompleted, needsGuardianCheck}`.
판정 차례: 400(미래 날짜) → 404 `MISSION_NOT_FOUND` → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN`(그 프로필 이름으로 적을 수 없음) → 403 `NOT_A_PARTICIPANT` → 422 `INVALID_METRIC`(STEPS 미션 아님) → 422 `CONSENT_REQUIRED`.
날짜는 보낸 `activityDate` 그대로다(걸음수는 「움직인 날」에 들어가지 않는다).

### POST /api/v1/missions/{missionId}/activity/timer — 대신 + 참여자 · FE 가 부르지 않음 · 걷을 후보
요청 `{profileId●, startedAt●, endedAt●(> startedAt), activeMinutes●(1~180)}` — `endedAt-startedAt` 분을 넘으면 그 값으로 자른다(최소 1분).
응답 200 `{activityDate(서버가 받은 날 KST), source:"TIMER", serverVerified:true, totalActiveMinutes(그날 누적, 모든 출처), missionProgress, missionCompleted}`.
판정 차례: 400(`endedAt ≤ startedAt`) → 404 `MISSION_NOT_FOUND` → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN` → 403 `NOT_A_PARTICIPANT` → 422 `INVALID_METRIC`(TIMER_MINUTES 미션 아님) → 422 `CONSENT_REQUIRED` → 422 `MISSION_NOT_ACTIVE`(오늘이 기간 밖).
- 활동 날짜는 기기의 `startedAt` 이 아니라 서버가 받은 날(KST)이다. 칸 끝과 같게, 지난 날이나 앞날에 「움직인 날」을 적지 못한다.
- 칸 있는 미션은 이 경로로 분을 쌓아도 진행되지 않는다.

### GET /api/v1/families/{familyId}/calendar?profileId=&from=&to= — 보호자는 식구 누구나 · 아이 계정은 자기 것만
한 사람의 날짜별 기록. 캘린더 · 하루 기록 · 이번 주 링이 이것 하나로 그린다. `from` ~ `to` 는 KST 날짜, 양끝 포함 42일까지. 셋 다 필수.
응답 200 `{profileId, from, to, days:[{date, minutes, plannedMinutes|null, entries[], stickers[], rest?}]}` — 날짜 오름차순.
- `entries[]` = `{missionId, title, minutes, verifiedBy|null, completed, sessions|null}`. `sessions[]` = `{position, phase, title, minutes, clip|null, verifiedBy|null, done}`. `clip` 은 미션 칸과 같은 모양(`mediaUrl` · `thumbnailUrl` 포함)이다. 칸 없는 운동은 `sessions` 가 null.
- `stickers[]` = `{cheerId, stickerId, fromProfileId, fromName, message, missionId, createdAt}` — 받은 칭찬(PRAISE) 가운데 스티커가 붙은 것. 고마워요(THANKS)는 싣지 않는다.
- `rest` 는 쉬는 날일 때만 `true` 로 싣는다. 앞날도 싣는다.
셈
- `minutes` = 그날 서버가 잰 활동(TIMER + VIDEO) 초 합 ÷ 60 내림. 리그의 해낸 날과 같은 값이다. 칸을 절반만 해도 인정되므로 다 한 날도 `plannedMinutes` 에 못 미칠 수 있다.
- `plannedMinutes` = 그날 선 운동의 칸 분 합. 선 운동이 없거나 합이 0 이면 null.
- 운동이 그날 서는지는 이어서 한 날 · 리그와 같은 규칙이다: 하루짜리는 그날, 여러 날짜리는 오늘 · 칸을 끝낸 날 · 한 칸도 안 한 채 지났으면 마지막 날.
- 여러 날짜리 미션의 칸 `done` 은 그 칸을 그날 끝냈을 때만 true. `completed` 는 마지막 칸을 끝낸 날에만 true.
- 넣는 날: 선 운동 · 움직인 기록 · 스티커 · 쉬는 날 중 하나라도 있는 날. 오늘 뒤의 날에는 운동 줄을 싣지 않는다.
판정 차례: 400(파라미터 누락 · 형식 · 날짜가 1900-01-01 ~ 2100-12-31 밖 · `to < from` · 43일 이상) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`(대상이 다른 가족) → 403 `NOT_A_PARENT`(아이 계정이 남의 기록).
읽기만 한다(진행도를 다시 저장하지 않는다). 42일 · 미션 여럿이어도 미션 · 칸 끝 · 활동 · 응원 · 쉬는 날을 한 번씩 읽는다.

### GET /api/v1/exercises?factor=&phase=&quiet=&q=&list=ALL|FAVORITES&profileId= — 로그인(`profileId` 를 주면 같은 가족)
전환기 별칭 `GET /clips` 도 같다(deprecated · 지금 FE 가 부르는 이름).
운동 구간(영상 속 한 동작) 목록. 운동 찾기 · 직접 짜기 · 홈 영상 줄이 쓴다.
응답 200 `{clips:[{clipId, videoId, startSec, endSec, title, factor|null, phase, homeOk, quiet, props, favorited, mediaUrl|null, thumbnailUrl|null}], total}`.
- 데이터는 `V132`(유튜브 구간) · `V161`(공단 영상, 한 편 = 구간 하나) 로 적재한 구간 가운데 켜져 있고(active) 운동인 것이다.
- `mediaUrl` · `thumbnailUrl`: 공단 영상 구간이면 mp4 주소 · 첫 장면 이미지이고 `startSec` 0 · `endSec` 영상 길이다. 유튜브 구간은 둘 다 null(지금처럼 videoId 로 유튜브 구간을 튼다).
- 연령대: `profileId` 의 연령대 구간만 준다. 없으면 호출 계정의 자기 프로필 연령대로 거르고, 계정에 프로필이 없으면 빈 목록이다. 어르신은 어르신 구간과 성인 구간을 함께 받는다(노인 전용 영상을 따로 만들지 않고 성인 영상을 똑같이 쓰기로 한 팀 결정. 유튜브 어르신 구간은 0개, 공단 어르신 영상은 101개). 성인은 어르신 구간을 받지 않는다.
- 영상 id 차례라 공단 영상(`0AUDLJ08S_…`)이 대부분의 유튜브 영상 앞에 선다. 같은 제목이면 공단 구간이 대표로 남는다.
- 거르는 차례: 연령대 → `factor`(한글 · 영문) · `phase` · `quiet`(true 면 조용한 구간만) · `q`(검색어) → `FAVORITES` 면 찜 → 같은 제목은 하나만(보는 연령대와 같은 구간 먼저, 그다음 영상 id · 시작 초 차례로 처음 것) → 영상 id · 시작 초 차례로 세워 앞 40개. `total` 은 자르기 전 수.
- `clipId` = `{videoId}-{startSec}`. `props` = 준비물이 있어야 하는 구간. `favorited` 는 `profileId` 없이 부르면 false.
판정 차례: 400(모르는 `factor` · `phase` · `list` 값) → 400 `PROFILE_REQUIRED`(`FAVORITES` 인데 `profileId` 없음) → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### POST /api/v1/exercises/{exerciseId}/favorite — 같은 가족
전환기 별칭 `POST /clips/{exerciseId}/favorite` 도 같다(deprecated).
구간 찜. 요청 `{profileId, favorited●}`. 응답 200 `{clipId, favorited}`. 프로필마다, 멱등(`exercise_favorites`, `V141`). 보호자가 아이 프로필의 찜을 바꿀 수 있다.
판정 차례: 400(`favorited` 없음) → 400 `PROFILE_REQUIRED` → 404 `CLIP_NOT_FOUND`(없음 · 꺼짐 · 운동 아님) → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### 운동 영상 · 구간 카탈로그
- `exercise_videos` 에 AI 영상 48편, `video_exercises` 에 구간 695개(운동 651 · 운동 아님 44)를 `V132` 가 넣는다. 모든 프로필(prod 포함)에 들어간다. local · compose · test 시드의 가짜 영상 4편(`sample00002~5`)은 시험용이다.
- 공단 「국민체력100 동영상 정보」 오픈API(공공데이터포털 15108846) 영상 890편을 `V161` 이 더한다(AI 커밋 `610959a` 의 `data/release/kspo_videos.csv` · `kspo_video_labels.csv`, `backend/scripts/kspo_videos_to_sql.py`). 한 편에 운동 하나라 자르지 않고 한 편 = 구간 하나(`clip_id` = `{videoId}-0`, seq 1, 0초 ~ 영상 길이)다.
  - 영상: `channel_name` 「국민체력100 동영상 정보」 · `channel_type` PUBLIC · `media_url`(mp4) · `thumbnail_url`(첫 장면 이미지) · 길이 · 연령 범위 · 요인(라벨) · 준비물(API 도구 칸) · 소음(라벨 quiet → QUIET) · 공간(라벨 home_ok → SMALL_ROOM). 유튜브 영상은 `media_url` · `thumbnail_url` 이 null 이다.
  - 연령대별 영상 수: 유소년 108 · 청소년 186 · 성인 476(공통 포함) · 어르신 120. 그중 운동 후보(`is_exercise`)는 유소년 107 · 청소년 141 · 성인 434 · 어르신 101 = 783개. 운동이 아니라고 라벨된 86개와 물속 영상(장소가 수영장뿐이거나 제목에 수영 · 아쿠아 등) 21개는 AI `catalog.py` 처럼 후보에서 빼려고 `is_exercise=false` 로 싣는다. 유아기 영상은 mp4 가 열리지 않아(302 → /error.html) AI 가 싣지 않았다.
  - 다음 판은 `kspo_videos_to_sql.py --ref <AI 커밋>` 으로 새 V 파일을 만든다. 판에서 빠진 공단 구간만 끄고 유튜브 구간은 건드리지 않는다. 거꾸로 `ai_clips_to_sql.py` 의 끄기 문장도 이제 유튜브 구간(`media_url` 이 null 인 영상)만 끈다.
- 구간 id(`clip_id`) = `{videoId}-{startSec}`. AI 가 영상을 다시 끊어도 운동 구간의 (videoId, startSec) 는 유지됐다(9/17 → 9/22 판에서 491/491).
- 단계(`phase`)는 영상 화면 표시 → 라벨 → 본운동 순으로 정한다. AI 새 판은 `backend/scripts/ai_clips_to_sql.py` 로 새 V 파일을 만들어 적재한다. 판에서 빠진 구간은 지우지 않고 `active=false`.
- 설계안의 `GET /videos/{videoId}/exercises` 는 없다.

### GET /api/v1/videos?list=ALL|FAVORITES|RECENT&profileId=&ageGroup=&factor=&cursor=&size=20 — 로그인 · FE 가 부르지 않음 · 걷을 후보
응답 200 `{videos:[{videoId, title, url("https://www.youtube.com/watch?v=" · 공단 영상은 mp4 주소), thumbnailUrl("https://i.ytimg.com/vi/{id}/hqdefault.jpg" · 공단 영상은 첫 장면 이미지), durationSec|null, label:{ageFrom, ageTo, factors[], intensity, space, noise, model}, badges[], favorited, maxProgress|null, mediaUrl|null}], nextCursor|null}`. 공단 영상(`V161`)도 목록에 들어온다 — `mediaUrl` 이 있으면 mp4 다.
`FAVORITES`·`RECENT` 는 profileId 필수(400). `profileId` 를 주면 같은 가족이어야 한다. size 1~100. `ageGroup` 안전 필터: 라벨 연령 범위와 교차하는 영상만(라벨 없는 영상은 아이 연령대에 나가지 않음). 커서 = 마지막 videoId(정렬 videoId 오름차순). `RECENT` 는 최근 시청순이고 커서를 무시한다.

### POST /api/v1/videos/{videoId}/favorite — 같은 가족 · FE 가 부르지 않음 · 걷을 후보
요청 `{profileId●, favorited●}`. 응답 200 `{videoId, profileId, favorited, favoritedAt|null}`.
오류: 404 `VIDEO_NOT_FOUND` → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.

### POST /api/v1/videos/{videoId}/progress — 대신 · FE 가 부르지 않음 · 걷을 후보
요청 `{profileId●, progress●(0~1), watchedSec●, missionId?}`.
응답 200 `{maxProgress, completed, creditedMinutes, verifiedBy|null, missionProgress|null}`.
규칙: 최대 진행률만 남김; 최초로 0.9 이상 도달 시 `activity_daily` source=VIDEO 로 영상 길이(분, 올림) 1회 적립(credited_at); 이미 적립되면 0. AI 영상은 길이 자료가 없어 `creditedMinutes` 가 0 이다. missionId 가 있으면 참여자 진행도 갱신(VIDEO_DONE 미션). 칸 있는 미션은 진행되지 않는다.
오류: 404 `VIDEO_NOT_FOUND` · 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN`(그 프로필 이름으로 적을 수 없음) · 422 `CONSENT_REQUIRED` · 404 `MISSION_NOT_FOUND` · 403 `NOT_A_PARTICIPANT`.

### POST /api/v1/coach/chat — 대신 · FE 가 부르지 않음 · 걷을 후보
요청 `{profileId●, conversationId?, question●(1~500)}`. `AiGateway.ask`.
응답 200 `{conversationId, messageId, answer, citations:[{index, sourceLabel, excerpt, url}], refused, refusalReason|null}`.
판정 차례: 400 → 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY` · 403 `FORBIDDEN`(그 프로필 이름으로 물을 수 없음) → 404 `CONVERSATION_NOT_FOUND` → 403 `FORBIDDEN`(다른 프로필의 대화) → 503.
USER·ASSISTANT 메시지 모두 저장(거부도 저장). 한 대화는 한 프로필의 것. AI 장애 · AI 응답을 읽지 못함(깨진 JSON · text/html · 칸 누락) → 503 `TEMPORARILY_UNAVAILABLE`(저장 안 함). refused=false 인데 인용 0 → 서버가 `no_citation_generated` 거부로 바꿔 저장. excerpt 는 AI 응답에 없으면 label 로 채움.

### GET /api/v1/families/{familyId}/report/weekly?weekStart= — 같은 가족 · FE 가 부르지 않음 · 걷을 후보
응답 200 `{weekStart, weekEnd, summary|null, missionStats:{total, completed}, members:[{profileId, name, activeMinutes, verifiedMinutes, completedMissions}], cheerCount}`.
그 주(월~일)에 겹치는 미션 집계. summary = 그 주 weekStart 의 run 중 가장 최근 것의 summary.

### GET /api/v1/facilities — ▲ 공공데이터 출처 확정 필요 → 이번 구현 범위 밖.

---

## 5. 레벨 · 경험치 (progress)

### GET /api/v1/profiles/{profileId}/progress — 같은 가족
아이 · 부모 프로필 모두 답한다.
응답 200 `{profileId, level, xp, levelFloorXp, nextLevelXp|null, streakDays, activeDays, achievements:[{code, title, description, earnedAt|null}], recentXp:[{kind, fromProfileId|null, amount, occurredOn, reason, at}]}`.
- `xp` = 경험치 원장(`progress_xp_events`, `V140`) 합. 원장은 INSERT 만 해서 한 번 쌓인 경험치는 줄지 않는다. (프로필, 종류, 키) 유니크라 같은 일로 두 번 쌓이지 않는다.

| 종류 | 언제 | 경험치 |
|---|---|---|
| `SESSION_DONE` | 칸 하나를 처음 끝냄 | +5 |
| `MISSION_DONE` | 미션 하나를 끝까지 함(그날 전부가 아니라 미션마다) | +20 |
| `STICKER` | 부모에게서 칭찬(PRAISE) 스티커를 받음, 같은 미션에 한 번. 고마워요 · 알리기는 주지 않는다 | +10 |
| `REMEASURE` | 다시 잰 회차가 생김(가장 이른 회차는 0) | +20 |

- `level` · `levelFloorXp` · `nextLevelXp`: 레벨 구간 0 · 80 · 200 · 360 · 560 · 800 · 1080 · 1400 · 1760 · 2160. 맨 위 레벨이면 `nextLevelXp` 는 null.
- `streakDays`(이어서 한 날): 운동이 잡힌 날 기준이다. 잡힌 날에 움직였으면 이어지고, 잡힌 날을 빼먹으면 끊긴다. 잡히지 않은 날과 쉬는 날은 건너뛴다(잡히지 않은 날에 스스로 움직였으면 +1). 예: 월 · 목 주 2회를 다 하면 목요일에 2일째. 읽을 때마다 센다.
- `activeDays` = 서버가 잰 활동이 있는 날 수(기간 제한 없음).
- `achievements` = 열두 개 전부. 받은 것은 `earnedAt`(실제로 판정한 시각), 아직이면 null. `FIRST_STEP` · `STREAK_3` · `FULL_SET` · `MIN_30` · `MIN_100` · `WEEKEND` · `TOGETHER` · `STREAK_7` · `REMEASURE` · `FIRST_STICKER` · `MIN_300` · `SIX_POWERS`. `SIX_POWERS` 는 조건이 정해지지 않아 늘 null 이다.
- `recentXp` = 최근 경험치 다섯 줄, 최근 것부터. 운동(칸 · 미션)은 하루를 한 줄로 묶고(그날 끝까지 한 미션이 있으면 `MISSION_DONE`, 없으면 `SESSION_DONE`, `amount` 는 그날 합), 스티커 · 다시 재기는 한 건마다 한 줄이다. 계약은 값(`kind` · `fromProfileId` · `amount` · `occurredOn`)이다.
- **`reason` · `at` 은 전환기 칸이다.** 지금 FE(`XpEvent {reason, amount, at}`)는 문장과 시각을 그대로 그려서, 없으면 `/kid/badges` 가 깨진다. FE 가 `kind` 로 문장을 짓게 되면 걷는다.
  - `reason` = FE 목과 같은 문장. `SESSION_DONE` 「운동을 했어요」 · `MISSION_DONE` 「운동을 다 했어요」 · `REMEASURE` 「키 · 몸무게를 새로 쟀어요」 · `STICKER` 「○○가 붙여 준 스티커」. 붙인 사람은 줄의 주인(그 프로필)이 부르는 말이다 — 주인이 아이면 보호자는 이름 대신 「엄마」(여) · 「아빠」(남)(알림 `PRAISE` 제목과 같은 규칙), 그 밖에는 이름. 지금 가족에 없는 사람이면 「가족」. 조사 이/가 는 받침에 맞춘다.
  - `at` = 원장에 적은 시각(`created_at`, ISO-8601 UTC). 하루로 묶은 운동 줄은 그날 가장 늦게 적은 시각.
- 「잡힌 날」 은 `progress.api.PlannedDays` 로 읽는다. coaching 이 구현한다(하루짜리 미션은 그날, 여러 날짜리는 캘린더와 같은 규칙).
오류: 404 `PROFILE_NOT_FOUND` · 403 `NOT_SAME_FAMILY`.
업적을 처음 받을 때 `AchievementEarned` 를 낸다(알림함이 아이에게 `ACHIEVEMENT` 를 만든다).

---

## 6. 가족 리그 (league)

### GET /api/v1/families/{familyId}/league?month=YYYY-MM — 같은 가족
한 달이 한 판이다. 가족끼리 「잡힌 날 중 해낸 날」 비율을 겨룬다.
응답 200 `{month, tier, rate|null, rank|null, groupSize, promote, demote, daysLeft, standings:[{familyName, rate|null, me}]}`.
- `month` 가 없으면 이번 달(KST). 이번 달은 방이 없으면 여기서 넣고(브론즈에서 시작) 지금 센다. 지난달은 정산 때 굳힌 값으로 답하고, 정산 전이면 먼저 정산한다.
- 달성률(`rate`, %): 아이마다 해낸 날 ÷ 센 날, 가족은 아이들 값의 평균 × 100 반올림. 셀 날이 없는 아이는 평균에서 빼고, 모두 없으면 null(0% 가 아니다). 부모는 셈에 들어가지 않는다.
  - 센 날 = 쉬는 날이 아니고, 잡힌 날이고, 미션을 만든 날(KST) 이후이고, (오늘 전이거나 그날 움직였다). 지난 날짜로 미션을 만들어 분모를 조작하지 못하게 만든 날 이후만 센다(`progress.api.PlannedDaysSinceCreated`).
  - 해낸 날 = 센 날 가운데 서버가 잰 활동(TIMER · VIDEO) 초 > 0 인 날.
- 방: 한 방 10가족까지. 올라가는 · 내려가는 자리 각 3(다이아는 올라가지 않고 브론즈는 내려가지 않는다). 방에 든 가족이 8 미만이면 `promote` · `demote` 는 0.
- 순위: 달성률 내림차순, 없는 집은 맨 아래. `rank` 는 나보다 높은 집 수 + 1 이라 동률이면 같은 값이다. 달성률이 없으면 null.
- `daysLeft` = 말일 − 오늘. 지난달은 0.
- `standings` 에는 가족 이름과 달성률만 싣는다(집 안 개인 기여는 싣지 않는다). 리그를 한 번도 연 적 없는 가족은 다른 집 순위표에 나오지 않는다.
- 매월 1일 00:10 KST 에 정산한다(0장 스케줄러). 표는 `league_rounds` · `league_members`(`V146`).
판정 차례: 400(`month` 형식) → 404 `FAMILY_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 422 `INVALID_DATE`(앞 달) → 404 `LEAGUE_NOT_FOUND`(그달 방에 없던 지난달) → 409 `LEAGUE_BUSY`.

---

## 7. 알림함 (notification)

알림은 행으로 저장한다(`notifications`, `V149`). 문구는 만들 때 굳힌다. (받는 프로필, 중복 키) 유니크라 같은 일로 두 번 생기지 않는다. 넣기는 `ON CONFLICT DO NOTHING` 이라 같은 알림이 동시에 와도 오류 없이 한 건만 남는다. 앱 밖 푸시는 없다.

| 종류 | 언제 | 누구에게 |
|---|---|---|
| `KID_DONE` | 아이가 「알리기」(DONE 응원)를 보냄. 마지막 칸을 끝냈다고 자동으로 만들지는 않는다 | 그 응원을 받은 부모 |
| `KID_THANKS` | 아이가 고마워요(THANKS)를 보냄 | 받은 부모 |
| `PRAISE` | 부모가 칭찬 · 스티커를 보냄 | 그 아이(보낸 이는 「엄마」 · 「아빠」) |
| `MISSION_READY` | 매일 07:30 그날 서는 운동. 07:30 뒤에 생긴 운동은 생길 때 | 아이 프로필만. 쉬는 날 · 걸음수 · 다 끝낸 운동은 만들지 않고, 다 끝내면 목록에서 빠진다. 07:30 뒤에 오늘을 쉬는 날로 바꿔도 목록에서 빠진다 |
| `ACHIEVEMENT` | 업적을 처음 받음 | 아이 프로필만, 14일 동안 보인다 |
| `REMEASURE` | 매일 09:00, 아이의 마지막 측정이 30일 이상 지남 | 부모 전원, 측정 회차당 한 번. 그 아이를 다시 재면 지난 회차로 만든 알림이 지워진다 |

- 알림은 원래 요청이 커밋된 뒤 알림 전용 스레드(2개, 대기열 1000)에서 새 트랜잭션으로 적는다. 알림이 실패해도 응원 · 칸 끝 · 미션 만들기 · 측정은 되돌려지지 않는다.
- 그래서 알림은 응답보다 몇 ms 늦게 생긴다. 응원 · 칸 끝 응답 직후 목록을 다시 읽으면 새 알림이 아직 없을 수 있다(다음 폴링에 보인다). 대기열까지 차면 그 알림은 버리고 로그만 남긴다.
- 미션이 지워지면(`MissionCancelled`) 그 미션의 알림을 지운다. 미션을 다 끝내면(`MissionCompleted`) 그 사람의 그 미션 `MISSION_READY` 가 빠진다.
- 측정을 등록하면(`FitnessTestRegistered`) 그 아이 가족의 부모 알림함에서 그 아이의 `REMEASURE` 를 지운다. 마지막 측정 회차로 만든 알림은 남긴다 — 지난 날짜를 나중에 적어 마지막 측정일이 그대로면 알림도 그대로다.
- 07:30 · 09:00 알림의 `createdAt` 은 0장 「시각 · 날짜」 대로다(늦게 돈 실행은 실제로 만든 시각).
- 오래된 알림 행을 지우는 보관 기간은 없다.

### GET /api/v1/notifications?profileId= — 대신
자기 프로필, 또는 보호자가 계정 없는 아이 프로필(아이 모드)의 알림함.
응답 200 `{items:[{notificationId, kind, title, body, aboutProfileId, fromProfileId|null, missionId|null, date, stickerId|null, createdAt, read, cheerId|null}], unread}` — 최신 30건(만든 시각이 늦은 것부터). `unread` 는 그 30건 안에서 센다. 오늘이 그 가족의 쉬는 날이면 `MISSION_READY` 는 목록에도 `unread` 에도 들어가지 않는다. `cheerId` 는 고마워요 답장(`replyToCheerId`)에 쓴다. `notificationId` 는 서버 UUID 다.
판정 차례: 400(`profileId` 없음) → 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `FORBIDDEN`(계정 있는 아이의 알림함을 부모 계정으로 엶 등).

### POST /api/v1/notifications/read — 대신
요청 `{profileId●, upTo?(ISO-8601 시각)}`. 응답 204.
`upTo` 를 보내면 그 시각까지 만든 알림만 읽음으로 바꾼다. 받은 목록의 가장 최근 `createdAt` 을 보내면 목록을 받은 뒤 새로 온 알림을 지킨다. 없으면 그 사람 알림 전부.
판정 차례: 400 → 404 `PROFILE_NOT_FOUND` → 403 `NOT_SAME_FAMILY` → 403 `FORBIDDEN`.

---

## 8. AI ↔ API 서버 (`shared.ai.AiGateway`)
AI 쪽 원문은 `family-fitness-ai/docs/인터페이스-명세.md` 다. 아래는 서버가 실제로 보내고 읽는 것이다.
- `{app.ai.base-url}/v1`, JSON, 인증 없음(내부망). AI 서비스는 `/v1` 아래 다섯 주소(assessment · trajectory · videos/search · coach/runs · coach/messages)와 `/health` 를 연다. 서버는 trajectory 를 부르지 않는다(10년 예측을 걷었다). 로컬에서는 AI 저장소의 `make serve`(uvicorn, 8000번)로 띄운다.
- 모드: `app.ai.mode=stub`(local · compose 기본, AI 없이 결정적 가짜 응답) · `http`(prod 기본, `APP_AI_MODE` 로 바꾼다).
- AI 오류 봉투 `{"error":{"code","message"}}` → 서버 예외: 409 → `AiRunInProgressException`(`RUN_IN_PROGRESS`) · 404 → `AiRunNotFoundException`(`RUN_NOT_FOUND`) · 400 → `AiBadRequestException`(503 `AI_BAD_REQUEST`) · 그 밖 상태 · 연결 실패 · 시간 초과 · 빈 응답 → `AiUnavailableException`(503 `TEMPORARILY_UNAVAILABLE`).
- 200 이어도 본문을 읽지 못하거나(깨진 JSON · text/html 오류 페이지) 서버 모양으로 바꾸지 못하면(칸 누락) `AiUnavailableException` 이다. 연결 실패와 같게 다룬다(재시도 · 대체 편성 · 503).
- 시간 한도 · 재시도: 연결 1s. 읽기 assessment 3s · 2회 / videos/search 4s · 2회 / coach/messages 10s · 0회 / POST coach/runs 2s · 0회 / GET coach/runs/{id} 3s. 재시도는 `AiUnavailableException` 에만, 200ms 부터 지수 백오프.
- `POST /v1/fitness/assessment` `{profile_ref, age, age_unit, sex, height_cm?, weight_kg?, measurements}` → `{input_level, age_group, child_scope:{focus_one|null}, parent_scope:{grade|null, peer_distribution[], factors[], copy{strength,focus}}, low_sample, disclaimer}`.
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
- 키 · 몸무게 · 측정값은 대상의 가장 최근 측정 회차 값이다. 그 회차에 체지방률 · 허리둘레를 적었으면 `measurements` 에 `003` · `004` 로 같이 싣는다(AI 가 BMI · 허리둘레-신장비와 함께 3등급 판정에 쓴다). `measurements` 가 비면 칸을 null 로 보낸다.
- `focus_factor`(보호자가 키워 주고 싶은 역량)와 `with_companion` 은 AI develop 의 `ConstraintsIn` 에 아직 없어 AI 가 받아서 버린다 — 그동안 http 모드에서는 이 값이 편성에 반영되지 않는다. 대체 편성 · 스텁은 둘 다 반영한다.
  - AI 로컬 브랜치 `feature/AI-kspo-video-api`(`b070698`, 아직 병합 전)가 두 칸을 받는다. `focus_factor` 는 측정으로 고른 가장 낮은 요인보다 먼저 대상 요인이 되고(처방 근거 · 규칙 편성 · LLM 편성 모두), `with_companion` 은 참여자를 늘리지 않고 LLM 문구에만 쓴다. 병합 · 배포 뒤부터 http 모드에도 반영된다.
  - 그 브랜치는 여덟 요인 밖의 이름을 400 으로 거절한다(빈 글자는 안 고른 것으로 본다). 서버는 `FitnessFactor` 의 한글 라벨이나 null 만 보내므로 걸리지 않는다.
- 응답 202 `{run_id, status:"running", poll_after_ms}`.

### 편성 결과 `GET /v1/coach/runs/{run_id}` — 서버가 읽는 것
```
{run_id, status: running|succeeded|failed|refused, steps:[{seq,name,status,summary}],
 proposal|null: {missions:[{kind, title, period:{start_date,end_date}, participants:[{ref,role}], duration_min, video_sec,
                            sessions:[{day_offset, phase, order, exercise_name, fitness_factor, duration_sec,
                                       video:{video_id,start_sec,end_sec,source?,media_url?}|null, evidence:[int]}],
                            copy:{child,parent}, reason}],
                 citations:[{index,label,chunk_id,url?}], notices:[]},
 refused, refusal_reason|null}
```
- 숫자 칸(`duration_min` · `video_sec` · `duration_sec` · `order`)은 비어 와도 읽는다. 9/17 앞의 옛 모양(세션마다 `duration_min`)이면 그 합을 목표 분으로 쓴다.
- `notices` 는 `CoachRunView.notices` 로 싣는다. 원문 proposal · steps JSON 은 coach_runs 에 그대로 저장한다.
- `video.source`(youtube · kspo) · `video.media_url`(공단 영상의 mp4 주소)은 없어도 읽는다(옛 응답은 유튜브로 본다). 칸에는 videoId · 구간만 사본으로 저장하고, mp4 · 첫 장면 주소는 조회 때 `exercise_videos` 에서 붙인다. AI 가 고른 공단 영상이 영상 표에 없으면(BE 에 실은 AI 판이 뒤처짐) 경고 로그를 남긴다 — 화면이 그 칸을 틀 수 없으니 `kspo_videos_to_sql.py` 로 판을 올린다.
- 제안 변환(`ProposalConverter`): missions[i] → 제안 항목 position=i, title, rationale=`reason`(비면 `copy.parent`), targetMetric=`TIMER_MINUTES`, video=(day_offset, order) 차례로 처음 영상이 있는 세션, participants=편성 대상(+ `withParent` 면 요청 보호자, 동반자), citations=evidence 가 가리키는 것(없으면 전체).
- 칸 변환: 세션을 (day_offset, order) 차례로 세워 position 1..n 을 매긴다. 한글 단계 → `WARMUP` · `MAIN` · `COOLDOWN`, `exercise_name` → title, video → clip 사본. `clip.title` 은 `V132` 클립 표의 동작 이름이고, 없으면 영상 제목이다. `video_id` 가 없거나 비면 clip 없는 칸이다. 저장은 `coach_run_proposal_sessions`(`V135`).
- 칸 분 배분(`SessionMinutesAllocator`, FE 목 `sessionsFor` 와 같다): 준비 · 정리 칸은 1분씩, 본운동 몫 = max(본운동 칸 수, 요청 분 − 준비 칸 수 − 정리 칸 수)를 본운동 칸에 나누고 나머지는 앞 칸부터 1분씩 더한다. 그래서 칸 분 합 = 요청 분 = targetValue 다(칸 수가 요청 분보다 많을 때만 합이 더 크다). 칸이 없으면 targetValue = `duration_min`(최소 1).
- 결과 처리

| AI 결과 | 서버 |
|---|---|
| `succeeded` | 제안 저장, `AWAITING_APPROVAL` |
| `refused` | FAILED(`NO_CITATIONS`), `ai_refused=true` · 거부 사유 |
| `failed` · 40회 폴링 안에 안 끝남 · 폴링 404 · 시작 호출의 연결 실패 · 시간 초과 · 5xx · 응답을 읽지 못함 | 라벨 기반 대체 편성(`LabelBasedProposalPlanner`, steps[1] 이 `partial`). 고를 요인(측정 백분위 · 보호자가 키워 주고 싶은 역량)이 없으면(측정 전 · 만 7~10세) 그 연령대 클립으로 「전신 기르기」 미션을 짠다(AI 규칙 편성과 같다). 짤 클립도 인용할 근거도 없으면 FAILED(`AI_FAILED`) |
| 폴링 한 번의 일시 오류(시간 초과 · 503 · 응답을 읽지 못함) | 그 회차만 건너뛰고 다음 폴링. 40회가 다 차면 위 대체 편성 |
| AI 409 · 400 · 서버가 제안을 저장하다 실패 | FAILED(`ERROR`) |
| 요청 뒤 대상의 동의를 거둠 | FAILED(`CONSENT_REQUIRED`) |

- 대체 편성은 클립 표(`V132` 유튜브 구간 + `V161` 공단 영상 구간)에서 대상 연령대 · 요인에 맞는 구간을 고른다. 어르신은 성인 클립도 후보로 넣고, 어르신 라벨 클립(공단 어르신 영상)을 앞에 세운다. 공단 영상 구간을 고르면 AI 와 같게 칸 video 에 `source:"kspo"` · `media_url` 을, 인용에 `chunkId` `kspo:<videoId>` · 라벨 「국민체력100 동영상 정보 · 제목」 · url mp4 주소를 싣는다. 준비 · 본 · 정리 가짓수는 AI `catalog.py` 와 같다: 10분까지 1·3·1, 20분까지 2·4·1, 35분까지 2·5·2, 그 위 3·6·3. 맞는 클립이 없으면(클립 표가 비었거나 조건에 걸려 본운동이 없으면) 본운동 한 칸이다. 스텁도 실제 클립 경계로 칸을 낸다 — 7~12세는 `Eg3GpTv7z8s`, 만 19세 위(성인 · 어르신)는 `IhShIA-WJNE`, 그 밖의 나이는 영상 없이.
- AI 는 같은 프로필이 든 실행이 돌고 있으면 409 를 낸다. 서버 잠금은 (대상, 날짜) 단위라, 같은 아이의 다른 날 편성이 동시에 돌면 뒤의 것은 AI 409 로 FAILED(`ERROR`)가 된다.
- AI 가 도중에 죽으면 폴링 40회를 다 채운 뒤 대체 편성으로 넘어간다. 연결이 곧바로 거절되면 약 60초(간격 1.5초 × 39), 응답이 없어 시간 초과가 나면 최대 약 220초(폴링마다 연결 1초 + 읽기 3초가 더해짐)다.

## 9. FE 요청서와 맞대 본 상태 (`feature/BE-35-launch-readiness`)
FE 화면 ↔ 주소 대응은 FE 요청서(`BACKEND_API.md`)가 원본이다. 여기에는 서버가 어디까지 했는지만 적는다.

| FE 요청서 | 요청 | 서버 |
|---|---|---|
| 1장 ① | 계정 없는 아이 이름으로 응원 | 있음 |
| 1장 ② · 2장 | 편성 몸통 · (프로필, 날짜) 잠금 | 있음 |
| 1장 ③ | `MissionView.sessions` · `CreateMissionRequest.sessions` · `ProposalView.sessions` · `participants[].doneSessions` | 있음 |
| 1장 ④ | AI 9/17 클립 형식 읽기 | 있음 |
| 1장 ⑤ | 레이더 민첩성 | 있음(latest 의 `radar`) |
| 1장 ⑥ | 편성 단계를 끝날 때마다 저장 | 없음 — 끝에 한 번에 저장 |
| 1장 ⑦ · ⑧ | 칸 끝 · calendar · progress | 있음(`/sessions/{seq}/complete` · `/calendar` · `/progress`). FE 가 부르는 `/done` 은 전환기 별칭으로 받고, `recentXp` 에 전환기 칸 `reason` · `at` 을 싣는다 |
| 2장 | `ProfileSummary.sex` · `/me` 의 `selfProfileId` | 있음 |
| 2장 | `photoUrl`(프로필 사진) | 없음 |
| 2장 | 구성원 추가 키 · 몸무게, latest 의 키 · 몸무게 | 있음 |
| 2장 | 응원 `stickerId` · `kind` · `replyToCheerId` | 있음 |
| 2장 | 미션 `dates[]` | 있음. `title` 은 1~50자 |
| 3장 | 측정 이력 · `coach/runs/latest` | 있음 |
| 3장 | availability · 클립(`/exercises`) · 클립 찜 · 받은 칭찬 · 알림 · 초대코드 미리 보기 · 리그 · 쉬는 날(`rest-cards`) | 있음. FE 가 부르는 `/clips` · `rest-days` 는 전환기 별칭으로 받는다 |
| 3장 | 사진 | 없음 |

아직 없는 것(주소 · 기능)
- 프로필 사진 저장소와 `photoUrl`.
- 편성 단계를 끝날 때마다 저장하는 것(지금은 끝에 한 번).
- 앱 밖 푸시(웹 푸시) · 「오늘 아직 안 했어요」 같은 재촉 알림 · 리그 결과(티어가 바뀌었다) 알림.
- 미션 고치기(PATCH) · 참여자 한 명 빼기 · 한 번에 등록한 묶음 지우기. 미션 만들기 멱등 키.
- 운동 느낌 · 운동할 수 있는 시간을 다음 편성에 싣는 것(AI 계약이 먼저 필요).
- 동의 이력 조회 · 만 14세 이상 본인 동의 절차 · 계정 탈퇴와 데이터 파기.
- 가족 이름 바꾸기 · 리그용 별칭(지금은 가족 이름이 다른 집 순위표에 그대로 나간다) · 빈 가족에서 나가기.
- 설계안의 `GET /videos/{videoId}/exercises`, `GET /facilities`.
- 토큰을 HttpOnly 쿠키로 옮기는 일.
