# 우리가족 체력키움 (family-fitness)

국민체력100 공개데이터 기반 가족 체력 서비스.
국민대학교 2026-2학기 학생설계형 알파프로젝트 / 2026년 국민체육진흥공단 공공데이터 활용 경진대회 출품작.

> 계정은 로그인 수단이고 프로필이 사람이다. 아이는 계정 없이 부모 계정 아래 프로필로 존재하고,
> 측정값은 저장 시점에 백분위로 굳으며, AI 코치의 제안은 보호자가 승인하기 전에는 미션이 되지 않는다.

## 상태

2026-12 기준 API 경로 52개(메서드까지 61개, 전환기 별칭과 개발용 `/dev/clock` 제외)가 있다. 프론트가 부르는 주소는 모두 열려 있고, 운영 서버(AWS Lightsail)에서 돈다.
2학기 후반(9~14주차)에 더한 것:

| 주차 | 기능 | PR |
|---|---|---|
| 9 | 성인 체력 나이 — 또래 분포 표에서 항목마다 기록이 중앙값이 되는 나이를 찾아 평균(`GET /profiles/{id}/fitness-age`) | #54 |
| 10 | 날씨 연동 — 기상청 단기예보로 비 · 눈 소식이 있는 날은 편성 장소를 집으로 바꾸고 안내 문구를 붙임 | #55 |
| 11 | 화면 조회 점검 — 주간 요약 · 가족 체력 지도의 N+1 수정, 화면별 SQL 수 회귀 시험, 1초 넘는 요청 로그 | #56 |
| 12 | 심사용 계정 정리 — 만든 지 31일이 지난 심사용 계정과 체험 가족을 매일 지움 | #57 |
| 13 | 체력 나이 개선 — 더 재면 좋은 항목 안내, 회차별 체력 나이 이력(`GET /profiles/{id}/fitness-age/history`) | #58 |

아직 없는 것은 프로필 사진, 앱 밖 푸시 · 재촉 알림, 미션 고치기 등이고 [docs/api-contract.md](./docs/api-contract.md) 9장에 적었다.
FE 가 부르지 않는 옛 주소 8개(대화 · 주간 요약 · 영상 셋 · 타이머 · 걸음수 · 보호자 확인)는 남아 있다. 10년 예측은 걷었다(FE 도 부르지 않는다).
프론트는 `cd backend && ./gradlew bootRun` 한 줄로 뜬 서버(H2 · 개발 로그인 · AI 스텁)에 바로 붙을 수 있다.
연동 방법은 [backend/README.md](./backend/README.md), 계약은 [docs/api-contract.md](./docs/api-contract.md).

| | |
|---|---|
| 백엔드 | [`backend/`](./backend) — Java · Spring Boot 4.1 · Spring Modulith · Java 25 (툴체인 자동 설치) |
| API 계약 | [`docs/api-contract.md`](./docs/api-contract.md) · 서버의 `/swagger-ui.html` |
| 구조 | [`docs/architecture.md`](./docs/architecture.md) · [`docs/erd.dbml`](./docs/erd.dbml) |
| 공공데이터 · AI | [`docs/public-data-and-ai.md`](./docs/public-data-and-ai.md) — 쓰는 공공데이터, 보고서에 쓸 AI 설명, 세 저장소 진행 상황과 남은 결정 |
| 설계 원본 | Notion(API 명세서 · AI 인터페이스 명세) · [FigJam 보드](https://www.figma.com/board/w0ap0PjCQhcgbZc7zSyTVf) |
| AI 서비스 | `family-fitness-ai` (FastAPI). `/v1` 아래 평가 · 추이 · 영상 검색 · 편성 · 대화 다섯 주소를 연다(서버는 추이를 부르지 않는다). 로컬(`bootRun`)은 AI 없이 뜨도록 스텁이 기본이고, 운영(`prod`)은 http 로 부른다 |

## 쓰는 공공데이터와 출처 표시

| 데이터 | 제공 | 이용 조건 | 서비스에서 |
|---|---|---|---|
| 체력측정 및 운동처방 종합 데이터 | 국민체육진흥공단 ([문화빅데이터플랫폼](https://www.bigdata-culture.kr/bigdata/user/data_market/detail.do?id=b3924850-aa65-11ec-8ee4-95f65f846b27), [공공데이터포털 94495](https://www.data.go.kr/data/94495/linkedData.do)) | 제공처의 이용 조건 | 또래 분포 표(백분위 · 체력 나이), 등급별 참가자 비율, AI 편성 근거(처방 기록) |
| 국민체력100 동영상 정보 오픈API | 국민체육진흥공단 ([공공데이터포털 15108846](https://www.data.go.kr/data/15108846/openapi.do)) | 공공누리 제1유형(출처표시) | 운동 영상 후보 452편 |
| 국민체력100 인증 기준 | 국민체육진흥공단 ([국민체력100](https://nfa.kspo.or.kr)) | 제공처의 이용 조건 | 인증 등급 판정 |
| 기상청_단기예보 조회서비스 | 기상청 ([공공데이터포털 15084084](https://www.data.go.kr/data/15084084/openapi.do)) | 공공누리 제1유형(출처표시) | 편성하는 날 낮의 강수형태 · 강수확률 |

출처 표시: 이 서비스는 국민체육진흥공단의 「국민체력100 동영상 정보」 와 기상청의 「단기예보 조회서비스」 를 이용했으며, 해당 저작물은 공공데이터포털(www.data.go.kr)에서 내려받을 수 있습니다.
데이터를 어디서 받아 어떻게 실었는지는 [docs/public-data-and-ai.md](./docs/public-data-and-ai.md) 에 적었다.

## 구조

```
backend/    Spring Boot 모듈러 모놀리스 (identity · fitness · activity · progress · coaching · league · notification)
docs/       API 계약 · 아키텍처 · ERD · ADR · 공공데이터와 AI 정리
.github/    CI (포맷 검사 + 빌드·테스트)
```

## 팀

유범익 (PM · AI/ML) · 이상진 (Frontend · UX · 활동량 연동) · 최비성 (Backend · 데이터)
지도교수: 김정우 (교양대학)
