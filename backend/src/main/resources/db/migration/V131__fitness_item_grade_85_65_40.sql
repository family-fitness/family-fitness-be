-- fitness: 항목 등급 기준을 1등급 ≥ 85 · 2등급 ≥ 65 · 3등급 ≥ 40 · 그 외 참가로 바꾼다(BE 설계안 ⑪).
-- 응답은 읽을 때 저장된 백분위에서 등급을 다시 셈하므로, 여기서는 표에 남은 옛 기준(90/75/50) 라벨만 맞춘다.
-- 백분위가 없는 항목(규준 없음)은 등급도 null 이라 건드리지 않는다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
update fitness_test_items
set grade = case
        when percentile >= 85 then '1등급'
        when percentile >= 65 then '2등급'
        when percentile >= 40 then '3등급'
        else '참가'
    end
where percentile is not null;
