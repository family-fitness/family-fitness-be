# backend

우리가족 체력키움 백엔드. Kotlin + Spring Boot 4.1.1, Java 25 툴체인, Spring Modulith 기반 모듈러 모놀리스.

## 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Docker 만 있으면 됩니다. `spring-boot-docker-compose` 가 [`compose.yaml`](./compose.yaml) 의
PostgreSQL 을 띄우고 접속 정보를 주입합니다.

| 명령 | 하는 일 |
|---|---|
| `./gradlew build` | 컴파일 · 테스트. 테스트는 Testcontainers 로 실제 PostgreSQL 을 띄웁니다 |
| `./gradlew spotlessCheck` | 포맷 검사 (ktlint 1.8.0). CI 가 이걸 먼저 돌립니다 |
| `./gradlew spotlessApply` | 포맷 자동 교정. 커밋 전에 돌리세요 |

## 모듈

Spring Modulith 모듈 경계 = ERD 묶음 = 패키지. 셋이 1:1 입니다
([`docs/erd.md`](../docs/erd.md) 의 「모듈 의존」).
`identity` 를 나머지 셋이 참조하고, 그 반대는 없습니다.

| 모듈 | 범위 | 상태 |
|---|---|---|
| `identity` | 계정 · 가족 · 구성원 · 응원 | 미착수 |
| `fitness` | 규준 · 측정 회차 · 예측 | 미착수 |
| `activity` | 활동 기록 | 미착수 |
| `coaching` | 영상 · 코치 실행 · 미션 · 인용 | 미착수 |

현재 소스는 애플리케이션 부트스트랩(`FamilyfitnessApplication.kt`)과 Testcontainers 설정뿐이고,
Flyway 마이그레이션은 아직 없습니다. 목표 스키마는 [`docs/erd.md`](../docs/erd.md), 그리로 가는
순서는 [DDD·헥사고날 전환 가이드](../docs/ddd-hexagonal-guide.md) 에 있습니다.

## 경계 밖

임베딩 생성과 RAG 검색은 별도 Python AI 서비스가 맡습니다. 이 애플리케이션은 `ai_documents` 를
직접 쓰지 않고, AI 서비스가 돌려준 `ai_document_id` 를 인용으로 저장하는 쪽입니다.
근거는 [ADR-001](../docs/adr/ADR-001-ddd-hexagonal-and-relational-data.md).
