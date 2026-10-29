-- coaching: 편성이 어떻게 끝났는지 실행 행에 남긴다. 화면에는 내지 않고 사용 기록 분석(AI 로 짠 비율, 대체 편성 까닭, 편성에 걸린 시간)에 쓴다.
--
-- plan_source      제안을 누가 짰는지(CoachPlanSource 이름). AI = AI 서비스가 짬, FALLBACK = AI 로 짜지 못해 BE 가 영상 라벨로 짬.
--                  FAILED 로 끝났으면 null 이다. AI 안에서 LLM 대신 규칙 편성이 돌았어도 AI 다(BE 는 그 차이를 받지 않는다).
-- fallback_reason  대체 편성으로 넘어간 까닭. 대체 편성 단계 요약에 쓰는 짧은 말 그대로다(연결 실패, 시간 초과, 실행 없음, 편성 실패,
--                  AI 사용 중, 심사용 계정의 오늘 AI 편성 몫이 끝남). AI 가 짰거나 FAILED 면 null 이다.
-- finished_at      RUNNING 을 벗어난 시각(제안을 붙였거나 FAILED 로 끝냄). 편성에 걸린 시간은 finished_at - created_at 이다.
--                  updated_at 은 승인, 거절 때 다시 바뀌어 이 용도로 쓸 수 없다.
--
-- 이 파일 앞에 끝난 행은 세 칸 모두 null 이다. 옛 행의 대체 편성 여부는 저장된 값만으로 가를 수 없어 채우지 않는다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 도는 문법만 쓴다.
alter table coach_runs add column plan_source varchar(10);
alter table coach_runs add column fallback_reason varchar(40);
alter table coach_runs add column finished_at timestamp with time zone;
alter table coach_runs add constraint ck_coach_runs_plan_source check (plan_source in ('AI', 'FALLBACK'));
