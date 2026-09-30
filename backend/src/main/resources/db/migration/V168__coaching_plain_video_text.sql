-- coaching: 영상과 클립 표에 실린 글에서 가운데 점을 걷어 낸다. 이 글은 운동 찾기와 미션 칸에 그대로 나간다.
-- 사이트에 보이는 글에는 가운데 점과 긴 대시를 쓰지 않기로 했다. 적용된 V132, V161~V165 는 고칠 수 없어 새 V 파일로 고친다.
-- 1) 동작 이름 여섯 가지는 문맥에 맞춰 고친다(가운데 점으로 이은 앞뒤, 좌우는 붙여 쓰고, 가슴과 어깨처럼 둘을 잇는 것은 「과」로).
--    영상 제목과 클립의 화면 이름, 운동 이름, 제목에 똑같이 적용한다. 미션과 제안 칸의 clip_title 은 만들 때 뜬 사본이라 그대로 둔다(V133).
-- 2) 남은 가운데 점은 AI copy.plain() 과 같이 쉼표로 바꾼다. 인용 이름(citation_label)은 AI 가 내보내는 모양과 같아진다
--    (국민체력100 운동처방동영상, 걷기). 준비물(equipment)은 매트, 밴드 처럼 쉼표로 잇는다.
-- 실린 자료에 긴 대시는 없다. 다시 싣는 스크립트(scripts/ai_clips_to_sql.py, kspo_videos_to_sql.py)도 같은 규칙으로 글을 다듬는다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 update ... set col = replace(...) 만 쓴다.
-- 3) 바로 앞 V167 이 짧은 제목의 가운데 점을 먼저 쉼표로 바꿨으므로, 그 쉼표 모양도 1) 과 같이 문맥에 맞춰 고친다.
--    인용 이름(citation_label)은 쉼표 모양이 맞으므로 건드리지 않는다.

update exercise_videos set title = replace(title, '가슴, 어깨', '가슴과 어깨') where title like '%가슴, 어깨%';
update exercise_videos set title = replace(title, '오른쪽, 왼쪽', '오른쪽과 왼쪽') where title like '%오른쪽, 왼쪽%';
update exercise_videos set title = replace(title, '앞, 옆으로', '앞과 옆으로') where title like '%앞, 옆으로%';
update exercise_videos set title = replace(title, '앞, 뒤로', '앞뒤로') where title like '%앞, 뒤로%';
update exercise_videos set title = replace(title, '좌, 우로', '좌우로') where title like '%좌, 우로%';
update exercise_videos set title = replace(title, '가슴, 몸통', '가슴과 몸통') where title like '%가슴, 몸통%';
update video_exercises set exercise_name = replace(exercise_name, '가슴, 어깨', '가슴과 어깨') where exercise_name like '%가슴, 어깨%';
update video_exercises set exercise_name = replace(exercise_name, '오른쪽, 왼쪽', '오른쪽과 왼쪽') where exercise_name like '%오른쪽, 왼쪽%';
update video_exercises set exercise_name = replace(exercise_name, '앞, 옆으로', '앞과 옆으로') where exercise_name like '%앞, 옆으로%';
update video_exercises set exercise_name = replace(exercise_name, '앞, 뒤로', '앞뒤로') where exercise_name like '%앞, 뒤로%';
update video_exercises set exercise_name = replace(exercise_name, '좌, 우로', '좌우로') where exercise_name like '%좌, 우로%';
update video_exercises set exercise_name = replace(exercise_name, '가슴, 몸통', '가슴과 몸통') where exercise_name like '%가슴, 몸통%';
update video_exercises set title = replace(title, '가슴, 어깨', '가슴과 어깨') where title like '%가슴, 어깨%';
update video_exercises set title = replace(title, '오른쪽, 왼쪽', '오른쪽과 왼쪽') where title like '%오른쪽, 왼쪽%';
update video_exercises set title = replace(title, '앞, 옆으로', '앞과 옆으로') where title like '%앞, 옆으로%';
update video_exercises set title = replace(title, '앞, 뒤로', '앞뒤로') where title like '%앞, 뒤로%';
update video_exercises set title = replace(title, '좌, 우로', '좌우로') where title like '%좌, 우로%';
update video_exercises set title = replace(title, '가슴, 몸통', '가슴과 몸통') where title like '%가슴, 몸통%';

update exercise_videos set title = replace(title, '가슴·어깨', '가슴과 어깨') where title like '%가슴·어깨%';
update exercise_videos set title = replace(title, '오른쪽·왼쪽', '오른쪽과 왼쪽') where title like '%오른쪽·왼쪽%';
update exercise_videos set title = replace(title, '앞·옆으로', '앞과 옆으로') where title like '%앞·옆으로%';
update exercise_videos set title = replace(title, '앞·뒤로', '앞뒤로') where title like '%앞·뒤로%';
update exercise_videos set title = replace(title, '좌·우로', '좌우로') where title like '%좌·우로%';
update exercise_videos set title = replace(title, '가슴·몸통', '가슴과 몸통') where title like '%가슴·몸통%';
update exercise_videos set title = replace(replace(title, ' · ', ', '), '·', ', ') where title like '%·%';
update exercise_videos set equipment = replace(replace(equipment, ' · ', ', '), '·', ', ') where equipment like '%·%';
update exercise_videos set citation_label = replace(replace(citation_label, ' · ', ', '), '·', ', ')
    where citation_label like '%·%';

update video_exercises set name_on_video = replace(name_on_video, '가슴·어깨', '가슴과 어깨') where name_on_video like '%가슴·어깨%';
update video_exercises set name_on_video = replace(name_on_video, '오른쪽·왼쪽', '오른쪽과 왼쪽') where name_on_video like '%오른쪽·왼쪽%';
update video_exercises set name_on_video = replace(name_on_video, '앞·옆으로', '앞과 옆으로') where name_on_video like '%앞·옆으로%';
update video_exercises set name_on_video = replace(name_on_video, '앞·뒤로', '앞뒤로') where name_on_video like '%앞·뒤로%';
update video_exercises set name_on_video = replace(name_on_video, '좌·우로', '좌우로') where name_on_video like '%좌·우로%';
update video_exercises set name_on_video = replace(name_on_video, '가슴·몸통', '가슴과 몸통') where name_on_video like '%가슴·몸통%';
update video_exercises set name_on_video = replace(replace(name_on_video, ' · ', ', '), '·', ', ') where name_on_video like '%·%';
update video_exercises set exercise_name = replace(exercise_name, '가슴·어깨', '가슴과 어깨') where exercise_name like '%가슴·어깨%';
update video_exercises set exercise_name = replace(exercise_name, '오른쪽·왼쪽', '오른쪽과 왼쪽') where exercise_name like '%오른쪽·왼쪽%';
update video_exercises set exercise_name = replace(exercise_name, '앞·옆으로', '앞과 옆으로') where exercise_name like '%앞·옆으로%';
update video_exercises set exercise_name = replace(exercise_name, '앞·뒤로', '앞뒤로') where exercise_name like '%앞·뒤로%';
update video_exercises set exercise_name = replace(exercise_name, '좌·우로', '좌우로') where exercise_name like '%좌·우로%';
update video_exercises set exercise_name = replace(exercise_name, '가슴·몸통', '가슴과 몸통') where exercise_name like '%가슴·몸통%';
update video_exercises set exercise_name = replace(replace(exercise_name, ' · ', ', '), '·', ', ') where exercise_name like '%·%';
update video_exercises set title = replace(title, '가슴·어깨', '가슴과 어깨') where title like '%가슴·어깨%';
update video_exercises set title = replace(title, '오른쪽·왼쪽', '오른쪽과 왼쪽') where title like '%오른쪽·왼쪽%';
update video_exercises set title = replace(title, '앞·옆으로', '앞과 옆으로') where title like '%앞·옆으로%';
update video_exercises set title = replace(title, '앞·뒤로', '앞뒤로') where title like '%앞·뒤로%';
update video_exercises set title = replace(title, '좌·우로', '좌우로') where title like '%좌·우로%';
update video_exercises set title = replace(title, '가슴·몸통', '가슴과 몸통') where title like '%가슴·몸통%';
update video_exercises set title = replace(replace(title, ' · ', ', '), '·', ', ') where title like '%·%';

