-- coaching: 편성 요청에 실어 온 대상의 키(cm)와 몸무게(kg)를 실행 행에 남긴다.
-- 측정 기록이 없는 아이도 편성을 받는다. 그런 아이는 AI 요청의 height_cm, weight_kg 에 이 값을 싣는다.
-- AI 는 요청을 받은 트랜잭션이 커밋된 뒤에 따로 부르므로, 그때 읽을 수 있게 조건 칸 옆에 둔다.
-- 안 보냈거나 이 파일 앞에 만든 행이면 null 이다. 형은 fitness_tests, profiles 의 height_cm, weight_kg 과 같다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 도는 문법만 쓴다.
alter table coach_runs add column height_cm numeric(4, 1);
alter table coach_runs add column weight_kg numeric(4, 1);
