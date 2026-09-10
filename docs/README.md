# 문서

| 문서 | 내용 |
|---|---|
| [api-contract.md](./api-contract.md) | API 계약 통합본 — Notion 「API 명세서」·AI 인터페이스 명세·Figma 보드를 합쳐 구현 기준으로 삼은 것. 엔드포인트별 요청·응답·오류 코드·불변식 |
| [architecture.md](./architecture.md) | 모듈 경계(Spring Modulith)·계층·모듈 간 호출 규칙·AI 서비스 경계 |
| [erd.dbml](./erd.dbml) | 실제 Flyway 스키마와 같은 ERD (dbdiagram.io 에 붙여넣기) |
| [adr/](./adr/) | 구조 결정 기록 |

원본 설계는 Notion(API 명세서 · 인터페이스 명세)과 [FigJam 보드](https://www.figma.com/board/w0ap0PjCQhcgbZc7zSyTVf)에 있다.
계약이 바뀌면 Notion 을 먼저 고치고 `api-contract.md` 와 코드를 맞춘다.
살아 있는 API 문서는 서버의 Swagger UI(`/swagger-ui.html`)다.
