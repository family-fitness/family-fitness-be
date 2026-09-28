-- coaching: 제안 · 미션의 영상은 유튜브 videoId(링크)만 저장한다. 화면이 videoId 로 유튜브 구간을 튼다.
-- AI 가 고른 영상이 exercise_videos 에 없으면(표가 비어 있거나 AI 릴리스가 먼저 바뀐 경우) 외래 키 때문에 제안에서 영상이 빠진다.
-- 그래서 두 외래 키를 걷는다. exercise_videos 에 있는 영상이면 조회 때 제목 · 배지를 붙이고, 없으면 유튜브 주소만 낸다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
alter table coach_run_proposal_items drop constraint fk_coach_run_proposal_items_video;
alter table missions drop constraint fk_missions_video;

-- 멈춘 RUNNING 정리(where status = 'RUNNING' and created_at < ?)를 주기마다 돈다.
create index ix_coach_runs_status_created on coach_runs (status, created_at);

-- 편성 한 번 = 한 사람의 하루(FE 요청서 1장 ②). 누구(subject_profile_id)의 어느 날(run_date)을 어떤 조건으로 짰는지 남긴다.
-- 옛 주간 실행 행이 있어 모두 null 을 허용한다. 회당 분은 전부터 있는 minutes_per_session 에 담는다(days_per_week 는 1).
-- focus_factor 는 FitnessFactor 이름(FLEXIBILITY 등), place 는 HOME · OUTDOOR(null 이면 장소를 가리지 않음).
alter table coach_runs add column subject_profile_id uuid;
alter table coach_runs add column run_date date;
alter table coach_runs add column quiet boolean;
alter table coach_runs add column place varchar(10);
alter table coach_runs add column focus_factor varchar(20);
alter table coach_runs add column with_parent boolean;
alter table coach_runs add constraint fk_coach_runs_subject foreign key (subject_profile_id) references profiles (id);
alter table coach_runs add constraint ck_coach_runs_place check (place in ('HOME', 'OUTDOOR'));

-- (프로필, 날짜) 잠금(결정 1): RUNNING 동안만 'profileId|runDate', 끝나면 null.
-- 유니크 인덱스에서 NULL 끼리는 중복으로 치지 않으므로(H2 · PostgreSQL 공통) 부분 인덱스 없이 RUNNING 하나만 남는다.
alter table coach_runs add column lock_key varchar(60);
create unique index ux_coach_runs_lock_key on coach_runs (lock_key);

-- 새 편성이 같은 (프로필, 날짜)의 승인 대기 제안을 거절하고, latest?profileId= 가 대상별 최근 실행을 찾는다.
create index ix_coach_runs_subject_date on coach_runs (subject_profile_id, run_date);
