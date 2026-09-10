-- 규준 구간의 나이 단위. 유아기 규준은 개월(48~83개월) 단위라 연 단위 구간과 구분해야 한다.
alter table fitness_norms add column age_unit varchar(4) default '세' not null;
alter table fitness_norms drop constraint uq_fitness_norm;
alter table fitness_norms add constraint uq_fitness_norm unique (item_code, sex, age_unit, age_from, age_to, percentile, source_year);
alter table fitness_norms add constraint ck_fitness_norms_age_unit check (age_unit in ('세', '개월'));
