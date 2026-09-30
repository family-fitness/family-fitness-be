-- coaching: 화면에 보이는 영상 준비물, 인용 이름, 영상 제목, 클립 제목에서 가운데 점을 쉼표로 바꾼다.
--   「매트 · 밴드」 → 「매트, 밴드」, 「국민체력100 운동처방동영상 · 걷기」 → 「국민체력100 운동처방동영상, 걷기」,
--   「가슴·어깨 펴기」 → 「가슴, 어깨 펴기」.
-- 이미 만든 미션과 제안의 칸 제목(clip_title)은 그때 굳힌 값이라 건드리지 않는다.
-- 다음 공단 표를 싣는 마이그레이션(scripts/kspo_videos_to_sql.py)은 AI 표의 인용 이름과 클립 제목을 다시 쓰므로,
-- AI 표에서도 가운데 점을 빼야 이 값이 유지된다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 update … where 만 쓴다.

update exercise_videos set equipment = replace(equipment, ' · ', ', ') where equipment like '% · %';
update exercise_videos set equipment = replace(equipment, '·', ', ') where equipment like '%·%' and char_length(equipment) < 55;

update exercise_videos set citation_label = replace(citation_label, ' · ', ', ') where citation_label like '% · %';
update exercise_videos set citation_label = replace(citation_label, '·', ', ') where citation_label like '%·%' and char_length(citation_label) < 110;

update exercise_videos set title = replace(title, ' · ', ', ') where title like '% · %';
update exercise_videos set title = replace(title, '·', ', ') where title like '%·%' and char_length(title) < 110;

update video_exercises set title = replace(title, ' · ', ', ') where title like '% · %';
update video_exercises set title = replace(title, '·', ', ') where title like '%·%' and char_length(title) < 55;

update video_exercises set exercise_name = replace(exercise_name, ' · ', ', ') where exercise_name like '% · %';
update video_exercises set exercise_name = replace(exercise_name, '·', ', ') where exercise_name like '%·%' and char_length(exercise_name) < 55;
