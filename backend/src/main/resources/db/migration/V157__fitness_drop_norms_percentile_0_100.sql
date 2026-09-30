-- fitness: 예전 백분위 표를 걷는다. 백분위는 이제 또래 분포 표(fitness_value_quantiles, V156)에서 AI stats/tables.py
-- percentile_of 와 같은 식으로 낸다. fitness_norms(V2 · V3 · V155)는 AI 저장소에서 지워진 age_band_value_quantiles.csv 로
-- 만든 21점 표였고, 점 사이를 선형 보간해 AI 와 백분위가 달랐다.
-- AI 백분위는 0~100 이라(표 밖 · 맨 끝 값) 저장 칸의 검사도 1~99 에서 0~100 으로 넓힌다. 지난 회차 값은 그대로 둔다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
drop table fitness_norms;
alter table fitness_test_items drop constraint ck_fitness_test_items_percentile;
alter table fitness_test_items add constraint ck_fitness_test_items_percentile
    check (percentile is null or (percentile between 0 and 100));
