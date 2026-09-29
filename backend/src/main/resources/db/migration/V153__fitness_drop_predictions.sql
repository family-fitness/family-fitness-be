-- fitness: 10년 예측을 걷는다(2026-09-16 결정, FE 는 더 부르지 않는다). POST /profiles/{profileId}/predictions 와
-- AI trajectory 호출이 없어져 이 두 표를 쓰는 곳이 없다. 개인 시계열이 없어 측정 이력 추이로 대신한다.
-- 가리키는 쪽(prediction_points → predictions)을 먼저 걷는다. predictions 가 건 외래 키(profiles · fitness_tests)는 표와 함께 없어진다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
drop table prediction_points;
drop table predictions;
