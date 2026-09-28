package kr.ac.kookmin.familyfitness.league.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 가족의 목표 달성률(%) — FE 목 familyRate(fe:src/mocks/league.ts:79-97)를 그대로 옮겼다(결정 41).
 *
 * <pre>
 * 센 날   셈 기간 안에서 쉬는 날이 아니고, 잡힌 날이고, (오늘 전이거나 그날 움직였다)
 *         — 오늘은 아직 하는 중이라 해냈을 때만 센다
 * 해낸 날 센 날 가운데 움직인 날
 * 아이    해낸 날 ÷ 센 날. 센 날이 없는 아이는 평균에서 뺀다(0 으로 세면 식구 수가 불리해진다)
 * 가족    아이들 값의 평균 × 100 을 반올림. 셀 아이가 없으면 null(0% 가 아니다)
 * </pre>
 *
 * 부모 · 식구 수 · 체력 점수는 셈에 들어가지 않는다. 잡힌 날 · 움직인 날 · 쉬는 날을 무엇으로 읽는지는 부르는 쪽이 정한다.
 */
public final class AchievementRate {
    private AchievementRate() {}

    /**
     * 아이 한 명의 재료.
     *
     * @param planned 잡힌 날 — 운동이 선 날 가운데 그 미션을 만든 날 이후
     * @param moved 움직인 날 — 서버가 잰 분(TIMER · VIDEO)이 0 보다 큰 날
     */
    public record ChildDays(Set<LocalDate> planned, Set<LocalDate> moved) {}

    /**
     * @param from 셈의 첫날(그달 1일)
     * @param until 셈의 끝날(양끝 포함) — 이번 달이면 오늘, 지난달이면 말일
     * @param today 오늘(KST)
     */
    public static @Nullable Integer of(
            List<ChildDays> children, Set<LocalDate> restDays, LocalDate from, LocalDate until, LocalDate today) {
        double sum = 0;
        int counted = 0;
        for (ChildDays child : children) {
            int days = 0;
            int done = 0;
            for (LocalDate day : child.planned()) {
                if (day.isBefore(from) || day.isAfter(until) || restDays.contains(day)) continue;
                boolean moved = child.moved().contains(day);
                if (!day.isBefore(today) && !moved) continue;
                days++;
                if (moved) done++;
            }
            if (days == 0) continue;
            sum += (double) done / days;
            counted++;
        }
        if (counted == 0) return null;
        return (int) Math.round(sum / counted * 100);
    }
}
