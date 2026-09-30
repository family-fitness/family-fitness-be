# 문서

| 문서 | 내용 |
|---|---|
| [api-contract.md](./api-contract.md) | API 계약 통합본 — Notion 「API 명세서」·FE 요청서(`BACKEND_API.md`)·AI 인터페이스 명세·Figma 보드를 합쳐 구현 기준으로 삼은 것. 엔드포인트별 요청·응답·오류 코드·불변식, FE 요청서 대비 구현 상태 |
| [architecture.md](./architecture.md) | 모듈 일곱 개의 경계(Spring Modulith)·의존 방향·SPI·도메인 이벤트·계층·호출 규칙·AI 서비스 경계·데이터 적재 |
| [erd.dbml](./erd.dbml) | Flyway 마이그레이션(V1~V161, 표 33개)에서 `backend/scripts/ddl_to_dbml.py` 로 생성한 ERD (dbdiagram.io 에 붙여넣기). 스크립트가 읽는 DDL 과 모르는 DDL 을 만났을 때의 동작은 [backend/README.md](../backend/README.md) 「데이터 스크립트」 참고 |
| [adr/](./adr/) | 구조 결정 기록 |
| [public-data-and-ai.md](./public-data-and-ai.md) | 팀 공유용 — 쓰는 공공데이터(측정 · 처방 · 공단 동영상 API)에서 확인한 사실, 보고서에 쓸 AI 설명과 쓰지 말 표현, 세 저장소 진행 상황과 남은 결정 |

원본 설계는 Notion(API 명세서 · 인터페이스 명세)과 [FigJam 보드](https://www.figma.com/board/w0ap0PjCQhcgbZc7zSyTVf)에 있다.
계약이 바뀌면 Notion 을 먼저 고치고 `api-contract.md` 와 코드를 맞춘다.
살아 있는 API 문서는 서버의 Swagger UI(`/swagger-ui.html`)다. local · compose 프로필에서만 켜지고 운영(prod)에서는 꺼져 있다(backend/README.md 「프로필」).
