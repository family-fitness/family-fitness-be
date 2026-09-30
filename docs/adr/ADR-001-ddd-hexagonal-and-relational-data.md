# ADR-001: 모듈러 모놀리스, 헥사고날 계층, 외부 AI 경계

상태: 채택 (2026-09-09 갱신)

## 결정

1. Spring Boot 애플리케이션 하나를 `identity` · `fitness` · `activity` · `coaching` 네 Spring Modulith 모듈로 나눈다.
   모듈 밖으로는 `api` 패키지만 연다. `fitness` · `activity` 는 `identity` 만, `coaching` 은 `identity` · `fitness` · `activity` 를 참조한다.
2. 각 모듈은 `domain` · `application` · `adapter` 계층을 둔다. 규칙이 있는 애그리게잇(`Family`, `CoachRun`, `Mission`, `FitnessTest`)은
   도메인 모델과 JPA 엔티티를 분리한다.
3. 데이터는 관계형(PostgreSQL)이고 Flyway 가 스키마의 정본이다. AI 제안 원문·실행 단계처럼 통째로만 읽는 것은 `text` 컬럼에 JSON 으로 둔다.
4. AI 서비스(FastAPI)는 검색·평가·편성·대화를 계산해 돌려주는 외부 시스템이다. 가족 권한, 프로필, 활동, 코치 실행, 미션, 대화·인용의
   영속 상태는 이 서버만 바꾼다. AI 응답이 승인 없이 미션이 되는 경로는 없다.

## 근거

- 팀이 셋(백엔드·AI·프론트)이고 5주 출품 범위라 서비스 분리보다 모듈 경계를 테스트로 지키는 편이 싸다.
- 미션 완료 판정이 활동과 측정 두 컨텍스트에 걸쳐서 `coaching → activity/fitness` 의존을 허용했다. 대신 방향은 한쪽뿐이며
  `ModularityTests` 가 순환을 막는다.
- Docker 가 없는 개발 환경도 있어, 마이그레이션을 PostgreSQL 과 H2 양쪽에서 도는 이식 가능한 SQL 로 유지한다.

## 결과

- 다른 모듈의 엔티티를 `@ManyToOne` 으로 묶지 않는다. `profileId`(UUID)만 들고 다니고 `ProfileQuery` 로 조회한다.
- 스키마 변경은 새 Flyway 파일로만 한다. `ddl-auto=validate`.
