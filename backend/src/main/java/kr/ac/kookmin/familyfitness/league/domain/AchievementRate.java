package kr.ac.kookmin.familyfitness.league.domain;

import java.time.LocalDate;
import java.util.HashSet;
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
 * 순위 점수(score) — 달성률만으로 줄을 세우면 편성을 미루고 쉬다 하루 해낸 가족이 100% 로 1등이 된다. 그래서 운동한 날 수에 로그를
 * 씌워 곱한다. 순위 · 월초 정산의 오르내림은 이 점수로 정하고, 달성률은 화면에 그대로 보인다.
 *
 * <pre>
 * 운동한 날  셈 기간 안에서 쉬는 날이 아니고, 셀 아이 가운데 누구든 해낸 날(가족마다 하루는 한 번)
 * 지난 날    셈 기간(그달 1일~끝날) 안에서 쉬는 날이 아닌 날. 오늘은 운동한 날일 때만 센다(위 「센 날」 의 오늘 규칙과 같다).
 *            잡힌 날이 아닌 날 · 미션을 만들기 전 날 · 가입하기 전 날도 센다 — 빼면 늦게 시작해 하루 해낸 가족이 다시 1등이 된다
 * 점수       달성률(반올림 전 값) × ln(1 + 운동한 날) ÷ ln(1 + 지난 날). 0~1 이고, 지난 날마다 다 해내면 1. 소수 넷째 자리로 반올림
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
     * 달성률과 순위 점수.
     *
     * @param rate 달성률(%)
     * @param score 순위 점수(0~1)
     */
    public record Result(int rate, double score) {}

    /**
     * 달성률만.
     *
     * @param from 셈의 첫날(그달 1일)
     * @param until 셈의 끝날(양끝 포함) — 이번 달이면 오늘, 지난달이면 말일
     * @param today 오늘(KST)
     */
    public static @Nullable Integer of(
            List<ChildDays> children, Set<LocalDate> restDays, LocalDate from, LocalDate until, LocalDate today) {
        Result result = measure(children, restDays, from, until, today);
        return result != null ? result.rate() : null;
    }

    /** 달성률과 순위 점수. 셀 아이가 없으면 null. 인자는 {@link #of} 와 같다. */
    public static @Nullable Result measure(
            List<ChildDays> children, Set<LocalDate> restDays, LocalDate from, LocalDate until, LocalDate today) {
        double sum = 0;
        int counted = 0;
        Set<LocalDate> doneDays = new HashSet<>();
        for (ChildDays child : children) {
            int days = 0;
            int done = 0;
            for (LocalDate day : child.planned()) {
                if (day.isBefore(from) || day.isAfter(until) || restDays.contains(day)) continue;
                boolean moved = child.moved().contains(day);
                if (!day.isBefore(today) && !moved) continue;
                days++;
                if (moved) {
                    done++;
                    doneDays.add(day);
                }
            }
            if (days == 0) continue;
            sum += (double) done / days;
            counted++;
        }
        if (counted == 0) return null;
        double fraction = sum / counted;
        int elapsed = 0;
        for (LocalDate day = from; !day.isAfter(until); day = day.plusDays(1)) {
            if (restDays.contains(day)) continue;
            if (!day.isBefore(today) && !doneDays.contains(day)) continue;
            elapsed++;
        }
        double score = elapsed == 0 ? 0 : fraction * Math.log1p(doneDays.size()) / Math.log1p(elapsed);
        return new Result((int) Math.round(fraction * 100), Math.round(Math.min(1, score) * 10_000) / 10_000.0);
    }
}
