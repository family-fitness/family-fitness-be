-- 로컬·테스트 전용 가짜 영상 4편(labeled_by = 'SEED'). id 는 실제 YouTube 영상이 아니고 라벨 값도 데모용 임의값이다.
-- 실제 AI 영상 48편(IdpXx2gm90o 포함)은 db/migration 의 V132 가 모든 프로필에 넣는다. 여기는 시험·데모용 행만 둔다.
-- 프로필 상호작용·미션이 참조할 수 있으므로 삭제하지 않고, 없는 행만 넣는다.
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00002', '가족이 함께하는 거실 5분 스트레칭', '국민체력100', 'PUBLIC', 300, 4, 64, '유연성', 'LOW', 'SMALL_ROOM', 'QUIET', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00002');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00003', '유소년 심폐지구력 키우기: 제자리 달리기 루틴', '국민체력100', 'PUBLIC', 480, 7, 12, '심폐지구력,순발력', 'MID', 'SMALL_ROOM', 'NORMAL', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00003');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00004', '성인 근력 운동: 맨몸 스쿼트와 플랭크', '국민체력100', 'PUBLIC', 720, 19, 64, '근력,근지구력', 'MID', 'SMALL_ROOM', 'QUIET', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00004');
insert into exercise_videos (video_id, title, channel_name, channel_type, duration_sec, age_from, age_to, factors, intensity, space, noise, equipment, labeled_by, label_model, collected_at)
select 'sample00005', '유아 놀이 체육: 균형 잡기와 점프', '국민체력100', 'PUBLIC', 420, 4, 6, '평형성,순발력', 'LOW', 'SMALL_ROOM', 'NORMAL', null, 'SEED', null, timestamp with time zone '2026-09-01 00:00:00+09'
where not exists (select 1 from exercise_videos where video_id = 'sample00005');
-- 이미 넣은 DB 의 제목도 새 글로 맞춘다. 화면 글에는 긴 대시를 쓰지 않는다.
update exercise_videos set title = '유소년 심폐지구력 키우기: 제자리 달리기 루틴' where video_id = 'sample00003' and labeled_by = 'SEED';
update exercise_videos set title = '성인 근력 운동: 맨몸 스쿼트와 플랭크' where video_id = 'sample00004' and labeled_by = 'SEED';
update exercise_videos set title = '유아 놀이 체육: 균형 잡기와 점프' where video_id = 'sample00005' and labeled_by = 'SEED';
