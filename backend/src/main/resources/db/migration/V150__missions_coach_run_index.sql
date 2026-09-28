-- coaching: missions.coach_run_id 인덱스. 외래 키(fk_missions_coach_run)만 있고 인덱스가 없었다 — PostgreSQL 은 외래 키에
-- 인덱스를 스스로 만들지 않아, 코치 실행 조회(GET /coach/runs/{id} · latest)의 미션 수 셈과 latest 의 「미션을 모두 지운 승인 실행」
-- 거르기가 부를 때마다 missions 표를 처음부터 끝까지 훑었다(SA-03). H2 는 외래 키에 인덱스를 만들어 시험에서는 드러나지 않았다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create index ix_missions_coach_run on missions (coach_run_id);
