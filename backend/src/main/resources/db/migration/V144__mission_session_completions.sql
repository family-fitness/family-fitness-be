-- coaching: 운동 한 칸 끝(POST /missions/{id}/sessions/{seq}/complete). 끝냈는지는 칸이 아니라 사람마다 든다.
--   한 행 = 한 사람이 한 칸을 처음 끝낸 기록. 같은 (미션, 칸, 사람)은 한 번뿐이라 다시 보내도 행이 늘지 않는다(기본 키).
--   position 은 mission_sessions 를 가리키지 않는다 — 칸 없는 미션은 position 1 을 미션 전체 한 칸으로 받는다(결정 35).
--   completed_on 은 서버가 받은 날(KST)이다. 여러 날짜리 미션이 어느 날 서는지(잡힌 날)를 이 날짜로 가른다.
--   active_seconds 는 인정한 영상 재생 초(요청 activeSeconds 를 endedAt − startedAt 으로 자른 값)다.
-- activity: 활동을 초로 쌓는다(결정 37). 분은 초 합에서 내림으로 셈하고, 「움직인 날」 은 그날 초 > 0 이다.
--   active_minutes 는 호환용으로 남기고 행의 초를 내림한 값으로 같이 적는다. 읽는 쪽은 active_seconds 를 본다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table mission_session_completions (
    mission_id     uuid        not null,
    position       smallint    not null,
    profile_id     uuid        not null,
    completed_at   timestamp with time zone not null,
    completed_on   date        not null,
    active_seconds integer     not null,
    verified_by    varchar(20) not null,
    constraint pk_mission_session_completions primary key (mission_id, position, profile_id),
    constraint fk_mission_session_completions_mission foreign key (mission_id) references missions (id),
    constraint fk_mission_session_completions_profile foreign key (profile_id) references profiles (id),
    constraint ck_mission_session_completions_position check (position >= 1),
    constraint ck_mission_session_completions_seconds check (active_seconds >= 0),
    constraint ck_mission_session_completions_verified
        check (verified_by in ('VIDEO_PROGRESS', 'TIMER', 'SELF_REPORT'))
);
-- 한 사람의 끝낸 칸과 그 날짜(잡힌 날 셈).
create index ix_mission_session_completions_profile on mission_session_completions (profile_id, mission_id);

alter table activity_daily add column active_seconds integer default 0 not null;
update activity_daily set active_seconds = active_minutes * 60;
alter table activity_daily add constraint ck_activity_daily_seconds check (active_seconds >= 0);
