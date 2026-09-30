-- coaching: 운동이 어땠는지(POST /missions/{id}/feedback). 한 사람이 한 미션에 한 줄이고, 다시 보내면 덮어쓴다.
-- feel 은 EASY(쉬웠어요) · GOOD(딱 좋아요) · HARD(힘들었어요). created_at 은 마지막으로 보낸 시각이다.
-- (mission_id, profile_id) 는 참여자 행을 가리킨다 — 참여자가 아닌 사람의 느낌은 들어가지 않는다.
-- 미션을 지우면(DELETE /missions/{id}) 앱이 이 행을 먼저 지운다. 다음 편성에 싣는 것은 AI 계약이 생긴 뒤에 한다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table mission_feedback (
    mission_id uuid        not null,
    profile_id uuid        not null,
    feel       varchar(10) not null,
    created_at timestamp with time zone not null,
    constraint pk_mission_feedback primary key (mission_id, profile_id),
    constraint fk_mission_feedback_participant foreign key (mission_id, profile_id)
        references mission_participants (mission_id, profile_id),
    constraint ck_mission_feedback_feel check (feel in ('EASY', 'GOOD', 'HARD'))
);
