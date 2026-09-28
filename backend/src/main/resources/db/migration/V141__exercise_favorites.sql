-- coaching: 운동 구간(클립) 찜. 프로필마다 따로 찜한다. 행 하나 = (프로필, 클립) 한 쌍이고, 찜을 풀면 행을 지운다.
-- clip_id 는 video_exercises 의 clip_id({video_id}-{start_sec})다. 새 AI 판에서 빠진 클립은 지우지 않고 끄므로(active=false)
-- 찜 행은 남는다. 목록은 켜진 클립만 보여 주므로 꺼진 클립의 찜은 보이지 않고, 다시 켜지면 되살아난다.
-- 영상 단위 찜(video_interactions.favorited)과는 따로다 — 직접 짜기는 영상이 아니라 구간을 담는다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table exercise_favorites (
    profile_id uuid        not null,
    clip_id    varchar(48) not null,
    created_at timestamp with time zone not null,
    constraint pk_exercise_favorites primary key (profile_id, clip_id),
    constraint fk_exercise_favorites_profile foreign key (profile_id) references profiles (id),
    constraint fk_exercise_favorites_clip foreign key (clip_id) references video_exercises (clip_id)
);
