-- coaching: 유튜브 클립 가운데 요인(fitness_factor)이 비어 있던 동작에 공단 영상의 같은 동작 요인을 채운다.
-- V132 는 AI clip_labels.csv 에서 이름이 똑같이 맞은(exact) 줄의 요인을 그대로 옮겼는데, 그 줄들에 요인이 비어 있었다.
-- 그래서 운동 찾기와 제안 순서에서 「다리뻗어 상체 숙이기」 같은 줄만 체력 요인 없이 「준비운동, 0:52」로 보였다.
-- AI kspo_videos.csv 에서 같은 동작 이름의 공단 요인이 하나인 것만 채운다. AI clip_labels.csv(ed71949)가 채운 다섯 동작과 같다.
-- 공단에서 요인이 둘로 갈리는 동작(엎드려 상체 들어올리기, 무릎 높여 제자리 달리기, 윗몸 말아 올리기)과 공단에 없는 동작은 그대로 둔다.
-- BE 는 공단 클립에 요인을 하나만 실어서 무릎 높여 제자리 달리기와 윗몸 말아 올리기가 하나처럼 보이지만, AI 표가 기준이다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 update 만 쓴다.

update video_exercises set fitness_factor = 'FLEXIBILITY'
where exercise_name = '다리뻗어 상체 숙이기' and fitness_factor is null and (source is null or source <> 'kspo');
update video_exercises set fitness_factor = 'FLEXIBILITY'
where exercise_name = '다리 벌려 앞으로 상체 숙이기' and fitness_factor is null and (source is null or source <> 'kspo');
update video_exercises set fitness_factor = 'FLEXIBILITY'
where exercise_name = '나비자세' and fitness_factor is null and (source is null or source <> 'kspo');
update video_exercises set fitness_factor = 'CARDIO'
where exercise_name = '걷기' and fitness_factor is null and (source is null or source <> 'kspo');
update video_exercises set fitness_factor = 'STRENGTH'
where exercise_name = '의자 앞에서 앉았다 일어서기' and fitness_factor is null and (source is null or source <> 'kspo');
