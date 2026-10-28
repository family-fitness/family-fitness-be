-- coaching: 편성을 시작할 때 본 날씨 안내(기상청 단기예보). 그날 낮(07~20시)에 비 · 눈 예보가 있거나 강수확률이 60% 이상이면
-- 장소를 고르지 않은 편성은 집(HOME)으로 짜고 그 까닭을, 밖(OUTDOOR)을 고른 편성은 장소를 그대로 두고 주의만 남긴다.
-- 화면에는 실행 응답의 notices 맨 앞에 싣는다. 날씨를 보지 않았거나(인증키 없음 · 조회 실패 · 예보 범위 밖) 비 소식이 없으면 null.
-- 옛 행은 null 이다.
alter table coach_runs add column weather_note varchar(120);
