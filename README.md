# 우리가족 체력키움 (family-fitness)

국민체력100 공개데이터 기반 가족 체력 서비스.
국민대학교 2026-2학기 학생설계형 알파프로젝트 / 한국스포츠정책과학원 공공데이터 활용 경진대회 출품작.

> 계정은 로그인 수단이고 프로필이 사람이다. 아이는 계정 없이 부모 계정 아래 프로필로 존재하고,
> 측정값은 저장 시점에 백분위로 굳으며, AI 코치의 제안은 보호자가 승인하기 전에는 미션이 되지 않는다.

## 상태

API 주소 34개가 있다(2026-09-28 develop 기준). 프론트 요청(FE 저장소 `BACKEND_API.md`)에 맞춰 편성을 「아이 한 명의 하루」로 바꾸고, 미션 칸 · 측정 이력 · AI 운동 영상 구간을 더했다.
그 요청의 새 주소(캘린더 · 레벨 · 칸 끝 · 리그 · 알림 등) 중 아직 없는 것은 [docs/api-contract.md](./docs/api-contract.md) 6장에 적었다.
프론트는 `cd backend && ./gradlew bootRun` 한 줄로 뜬 서버(H2 · 개발 로그인 · AI 스텁)에 바로 붙을 수 있다.
연동 방법은 [backend/README.md](./backend/README.md), 계약은 [docs/api-contract.md](./docs/api-contract.md).

| | |
|---|---|
| 백엔드 | [`backend/`](./backend) — Java · Spring Boot 4.1 · Spring Modulith · Java 25 (툴체인 자동 설치) |
| API 계약 | [`docs/api-contract.md`](./docs/api-contract.md) · 서버의 `/swagger-ui.html` |
| 구조 | [`docs/architecture.md`](./docs/architecture.md) · [`docs/erd.dbml`](./docs/erd.dbml) |
| 설계 원본 | Notion(API 명세서 · AI 인터페이스 명세) · [FigJam 보드](https://www.figma.com/board/w0ap0PjCQhcgbZc7zSyTVf) |
| AI 서비스 | `family-fitness-ai` (FastAPI). `/v1` 아래 평가 · 추이 · 영상 검색 · 편성 · 대화 다섯 주소를 연다. 로컬(`bootRun`)은 AI 없이 뜨도록 스텁이 기본이고, 운영(`prod`)은 http 로 부른다 |

## 구조

```
backend/    Spring Boot 모듈러 모놀리스 (identity · fitness · activity · coaching)
docs/       API 계약 · 아키텍처 · ERD · ADR
.github/    CI (포맷 검사 + 빌드·테스트)
```

## 팀

유범익 (PM · AI/ML) · 이상진 (Frontend · UX · 활동량 연동) · 최비성 (Backend · 데이터)
지도교수: 김정우 (교양대학)
