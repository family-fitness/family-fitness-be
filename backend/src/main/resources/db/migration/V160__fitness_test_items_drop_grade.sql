-- fitness: 항목마다 굳혀 두던 등급 칸을 걷는다. 등급은 이제 국민체력100 인증서처럼 한 사람에게 하나만 매기고
-- (AI stats/tables.py certify 와 같은 규칙), 저장하지 않고 읽을 때 셈한다 — 기준표(V154)가 고정이라 저장된 측정값 ·
-- 키 · 몸무게 · 체지방률 · 허리둘레 · 측정일 나이로 언제나 같은 등급이 나온다. 잰 값 · 백분위 · 구간은 그대로 둔다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
alter table fitness_test_items drop column grade;
