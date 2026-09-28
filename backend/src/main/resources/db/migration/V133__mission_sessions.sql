-- coaching: 미션 한 건을 준비 · 본 · 정리 칸으로 나눈다. 칸은 미션을 만들 때 한 번 쓰고 고치지 않는다.
-- 칸의 영상 구간은 사본이다(클립 표에 FK 를 걸지 않는다 — 클립 표를 다시 적재해도 지난 미션이 바뀌지 않게).
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table mission_sessions (
    mission_id uuid         not null,
    position   smallint     not null,
    phase      varchar(10)  not null,
    title      varchar(120) not null,
    factor     varchar(20),
    minutes    smallint     not null,
    video_id   varchar(32),
    start_sec  integer,
    end_sec    integer,
    clip_title varchar(120),
    constraint pk_mission_sessions primary key (mission_id, position),
    constraint fk_mission_sessions_mission foreign key (mission_id) references missions (id),
    constraint ck_mission_sessions_position check (position >= 1),
    constraint ck_mission_sessions_phase check (phase in ('WARMUP', 'MAIN', 'COOLDOWN')),
    constraint ck_mission_sessions_minutes check (minutes >= 1),
    constraint ck_mission_sessions_clip_start check ((video_id is null) = (start_sec is null)),
    constraint ck_mission_sessions_clip_rest check (video_id is not null or (end_sec is null and clip_title is null)),
    constraint ck_mission_sessions_start check (start_sec is null or start_sec >= 0),
    constraint ck_mission_sessions_range check (end_sec is null or end_sec > start_sec)
);
