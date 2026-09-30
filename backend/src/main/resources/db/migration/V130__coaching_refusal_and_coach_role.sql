-- coaching: AI 가 편성을 거부한 사실과 편성 역할(주행자·동반자·응원)을 남긴다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
alter table coach_runs add column ai_refused boolean default false not null;
alter table coach_runs add column ai_refusal_reason varchar(60);
alter table mission_participants add column coach_role varchar(10);
