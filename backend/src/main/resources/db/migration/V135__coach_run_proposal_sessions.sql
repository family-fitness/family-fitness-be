-- coaching: 코치 제안 항목의 칸(준비 · 본 · 정리). mission_sessions 와 같은 칸에 제안 항목 키(coach_run_id, item_position)를 붙였다.
-- 승인은 이 칸을 mission_sessions 로 차례 그대로 복사한다. 칸의 영상 구간은 사본이다(클립 표에 FK 를 걸지 않는다 —
-- 클립 표를 다시 적재해도 지난 제안이 바뀌지 않게). 칸 없는 제안(옛 실행)은 행이 없다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table coach_run_proposal_sessions (
    coach_run_id  uuid         not null,
    item_position smallint     not null,
    position      smallint     not null,
    phase         varchar(10)  not null,
    title         varchar(120) not null,
    factor        varchar(20),
    minutes       smallint     not null,
    video_id      varchar(32),
    start_sec     integer,
    end_sec       integer,
    clip_title    varchar(120),
    constraint pk_coach_run_proposal_sessions primary key (coach_run_id, item_position, position),
    constraint fk_coach_run_proposal_sessions_item foreign key (coach_run_id, item_position)
        references coach_run_proposal_items (coach_run_id, position),
    constraint ck_coach_run_proposal_sessions_position check (position >= 1),
    constraint ck_coach_run_proposal_sessions_phase check (phase in ('WARMUP', 'MAIN', 'COOLDOWN')),
    constraint ck_coach_run_proposal_sessions_minutes check (minutes >= 1),
    constraint ck_coach_run_proposal_sessions_clip_start check ((video_id is null) = (start_sec is null)),
    constraint ck_coach_run_proposal_sessions_clip_rest check (video_id is not null or (end_sec is null and clip_title is null)),
    constraint ck_coach_run_proposal_sessions_start check (start_sec is null or start_sec >= 0),
    constraint ck_coach_run_proposal_sessions_range check (end_sec is null or end_sec > start_sec)
);
