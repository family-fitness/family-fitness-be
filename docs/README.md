# 문서

| 문서 | 내용 |
|---|---|
| [api-contract.md](./api-contract.md) | API 계약 통합본 — Notion 「API 명세서」·FE 요청서(`BACKEND_API.md`)·AI 인터페이스 명세·Figma 보드를 합쳐 구현 기준으로 삼은 것. 엔드포인트별 요청·응답·오류 코드·불변식, FE 요청서 대비 구현 상태 |
| [architecture.md](./architecture.md) | 모듈 일곱 개의 경계(Spring Modulith)·의존 방향·SPI·도메인 이벤트·계층·호출 규칙·AI 서비스 경계·데이터 적재 |
| [erd.dbml](./erd.dbml) | Flyway 마이그레이션(V1~V154, 표 32개)에서 `backend/scripts/ddl_to_dbml.py` 로 생성한 ERD (dbdiagram.io 에 붙여넣기). 스크립트가 읽는 DDL 과 모르는 DDL 을 만났을 때의 동작은 [backend/README.md](../backend/README.md) 「데이터 스크립트」 참고 |
| [adr/](./adr/) | 구조 결정 기록 |

원본 설계는 Notion(API 명세서 · 인터페이스 명세)과 [FigJam 보드](https://www.figma.com/board/w0ap0PjCQhcgbZc7zSyTVf)에 있다.
계약이 바뀌면 Notion 을 먼저 고치고 `api-contract.md` 와 코드를 맞춘다.
살아 있는 API 문서는 서버의 Swagger UI(`/swagger-ui.html`)다.
