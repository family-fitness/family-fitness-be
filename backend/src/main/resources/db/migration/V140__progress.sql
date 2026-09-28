-- progress: 경험치 원장과 받은 업적. 둘 다 넣기만 하고 고치거나 지우지 않는다(결정 23 — 한 번 받은 경험치 · 업적은 줄지 않는다).
-- 경험치 = 원장 합, 레벨 = 경험치의 구간이라 원장이 줄지 않는 한 레벨도 내려가지 않는다. 앱에도 UPDATE · DELETE 경로가 없다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.

-- (1) 경험치 원장. 한 줄이 한 번의 적립이다.
--     kind       SESSION_DONE(칸 처음 끝냄 +5) · MISSION_DONE(운동 하나 끝까지 +20) · STICKER(칭찬 스티커 +10) · REMEASURE(다시 재기 +20)
--     source_key 같은 적립을 가르는 키 — SESSION_DONE "missionId:position" · MISSION_DONE missionId ·
--                STICKER missionId(운동에 붙지 않은 스티커는 cheerId) · REMEASURE fitnessTestId.
--                (profile_id, kind, source_key) 유니크가 같은 칸 · 같은 운동의 두 번째 적립을 막는다.
--     phase · factor  끝낸 칸의 단계 · 체력 요인(SESSION_DONE 만). 업적 「준비부터 정리까지」 · 「여섯 가지 힘」 판정에 쓴다.
--                     factor 는 mission_sessions.factor 처럼 한글 요인 이름이다.
--     occurred_on 그 일이 있었던 날(KST) — 칸을 끝낸 날 · 스티커를 받은 날 · 잰 날.
--     mission_id 는 기록용이라 외래 키를 걸지 않는다(미션은 coaching 의 표이고 지우는 경로가 없다).
create table progress_xp_events (
    id              uuid        not null,
    profile_id      uuid        not null,
    kind            varchar(20) not null,
    source_key      varchar(80) not null,
    amount          integer     not null,
    from_profile_id uuid,
    mission_id      uuid,
    phase           varchar(10),
    factor          varchar(20),
    occurred_on     date        not null,
    created_at      timestamp with time zone not null,
    constraint pk_progress_xp_events primary key (id),
    constraint fk_progress_xp_events_profile foreign key (profile_id) references profiles (id),
    constraint fk_progress_xp_events_from foreign key (from_profile_id) references profiles (id),
    constraint uq_progress_xp_events_source unique (profile_id, kind, source_key),
    constraint ck_progress_xp_events_kind check (kind in ('SESSION_DONE', 'MISSION_DONE', 'STICKER', 'REMEASURE')),
    constraint ck_progress_xp_events_amount check (amount > 0),
    constraint ck_progress_xp_events_phase check (phase is null or phase in ('WARMUP', 'MAIN', 'COOLDOWN'))
);
-- 최근 경험치 줄(적은 차례) · 합계.
create index ix_progress_xp_events_recent on progress_xp_events (profile_id, created_at);
-- 그날 끝낸 칸의 단계 · 최근 줄의 하루치 합.
create index ix_progress_xp_events_day on progress_xp_events (profile_id, occurred_on);

-- (2) 받은 업적. 한 사람에 업적 하나는 한 번 — 처음 받은 시각을 그대로 둔다.
create table progress_achievements (
    profile_id uuid        not null,
    code       varchar(20) not null,
    earned_at  timestamp with time zone not null,
    constraint pk_progress_achievements primary key (profile_id, code),
    constraint fk_progress_achievements_profile foreign key (profile_id) references profiles (id)
);
