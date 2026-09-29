-- fitness: 측정 회차에 체지방률 · 허리둘레를 같이 적는다. 둘 다 고를 수 있다(안 적으면 null).
-- 국민체력100 인증 3등급은 BMI · 체지방률 · 허리둘레-신장비를 보는데, 이 둘을 받지 않아 3등급 판정이 거의 안 됐다.
-- 키 · 몸무게처럼 그 회차 값으로 굳힌다. 범위(체지방률 3~60 · 허리둘레 30~200)는 요청 검사가 막는다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
alter table fitness_tests add column body_fat_pct numeric(4, 1);
alter table fitness_tests add column waist_cm numeric(4, 1);
