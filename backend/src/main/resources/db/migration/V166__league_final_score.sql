-- 리그 순위 점수(0~1). 순위 · 월초 정산의 오르내림을 달성률 대신 이 값으로 정한다 — 달성률 × ln(1 + 운동한 날) ÷ ln(1 + 지난 날).
-- 하루만 해낸 100% 가족이 날마다 해낸 가족 앞에 서지 않게 한다. 정산 때 final_rate 와 같이 채운다.
-- 이 칸을 만들기 전에 정산한 달은 null 로 남는다 — 그 달을 다시 보여 줄 때는 final_rate ÷ 100 을 점수로 쓴다(LeagueService).
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
alter table league_members add column final_score double precision;

alter table league_members
    add constraint ck_league_members_score check (final_score is null or (final_score >= 0 and final_score <= 1));
