-- 로컬·테스트 전용 샘플 영상 라벨. 국민체력100 유튜브 채널의 공개 영상 ID 예시이며 라벨 값은 데모용 임의값이다 (labeled_by = 'SEED').
-- 프로필 상호작용·미션이 참조할 수 있으므로 삭제하지 않고, 없는 행만 넣는다.
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'IdpXx2gm90o', '초등학생의 기초체력향상과 운동능력발달을 위한 운동', '국민체력100', 'PUBLIC', 600, 7, 12, '유연성,근지구력', 'LOW', 'SMALL_ROOM', 'QUIET', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'IdpXx2gm90o');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00002', '가족이 함께하는 거실 5분 스트레칭', '국민체력100', 'PUBLIC', 300, 4, 64, '유연성', 'LOW', 'SMALL_ROOM', 'QUIET', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00002');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00003', '유소년 심폐지구력 키우기 — 제자리 달리기 루틴', '국민체력100', 'PUBLIC', 480, 7, 12, '심폐지구력,순발력', 'MID', 'SMALL_ROOM', 'NORMAL', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00003');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00004', '성인 근력 운동 — 맨몸 스쿼트와 플랭크', '국민체력100', 'PUBLIC', 720, 19, 64, '근력,근지구력', 'MID', 'SMALL_ROOM', 'QUIET', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00004');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00005', '유아 놀이 체육 — 균형 잡기와 점프', '국민체력100', 'PUBLIC', 420, 4, 6, '평형성,순발력', 'LOW', 'SMALL_ROOM', 'NORMAL', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00005');
