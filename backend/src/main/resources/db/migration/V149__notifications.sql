-- notification: 알림함(GET /notifications · POST /notifications/read). 한 행 = 한 사람(profile_id)이 받은 알림 한 건.
--   알림은 일이 생긴 그때 행으로 저장한다(FE 목은 읽을 때 셈하지만 서버는 쌓는다 — 결정 45). 문구(title · body)는 만든 순간 굳힌다.
--   kind         KID_DONE · KID_THANKS(부모에게) · PRAISE · MISSION_READY · ACHIEVEMENT(아이에게) · REMEASURE(부모에게)
--   about_profile_id  누구에 관한 알림인가(부모 알림이면 그 아이) · from_profile_id 보낸 사람(스티커 · 칭찬 · 고마워요)
--   mission_id   외래 키를 걸지 않는다 — 미션이 지워지면 coaching 의 MissionCancelled 이벤트를 듣고 그 미션의 알림을 지운다.
--   cheer_id     알림을 만든 응원. 고마워요 답장(replyToCheerId)을 이 값으로 고른다
--   event_date   그 일이 있었던 날(KST). 응답의 date 칸이다
--   dedupe_key   같은 일로 알림이 두 번 생기지 않게 가르는 키(done-{cheerId} · ready-{missionId}-{date} · remeasure-{kidId}-{testedOn} 등).
--                (profile_id, dedupe_key) 유니크 — 같은 일이라도 받는 사람마다 한 건이다(부모 둘 · 형제 둘).
--   read_at      읽은 시각. null 이면 안 읽음
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table notifications (
    id               uuid         not null,
    profile_id       uuid         not null,
    kind             varchar(20)  not null,
    title            varchar(150) not null,
    body             varchar(200),
    about_profile_id uuid,
    from_profile_id  uuid,
    mission_id       uuid,
    cheer_id         uuid,
    sticker_id       varchar(20),
    event_date       date,
    created_at       timestamp with time zone not null,
    read_at          timestamp with time zone,
    dedupe_key       varchar(100) not null,
    constraint pk_notifications primary key (id),
    constraint fk_notifications_profile foreign key (profile_id) references profiles (id),
    constraint fk_notifications_about foreign key (about_profile_id) references profiles (id),
    constraint fk_notifications_from foreign key (from_profile_id) references profiles (id),
    constraint fk_notifications_cheer foreign key (cheer_id) references cheers (id),
    constraint ck_notifications_kind
        check (kind in ('KID_DONE', 'KID_THANKS', 'PRAISE', 'MISSION_READY', 'ACHIEVEMENT', 'REMEASURE'))
);
create unique index ux_notifications_dedupe on notifications (profile_id, dedupe_key);
-- 알림함 목록(최신 30건) · 읽음 처리.
create index ix_notifications_profile_created on notifications (profile_id, created_at desc);
-- 미션을 끝내거나 지웠을 때 그 미션의 알림 찾기.
create index ix_notifications_mission on notifications (mission_id);
