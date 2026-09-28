package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.VerifiedSummary;

/** activity 모듈의 가짜: 일별·출처별 기록을 메모리에 쌓고 합계를 낸다. */
public class FakeActivity implements ActivityRecorder, ActivityQuery {
    public record Key(UUID profileId, LocalDate date, ActivitySource source) {}

    public final Map<Key, DailyActivity> rows = new LinkedHashMap<>();

    /** 합계 조회 횟수 — 미션 진행도를 몇 번 다시 셌는지 본다. */
    public int totalsCalls = 0;

    @Override
    public DailyActivity overwriteSteps(UUID profileId, LocalDate activityDate, int steps) {
        DailyActivity row = new DailyActivity(profileId, activityDate, ActivitySource.MANUAL, steps, 0);
        rows.put(new Key(profileId, activityDate, ActivitySource.MANUAL), row);
        return row;
    }

    @Override
    public DailyActivity addActiveMinutes(UUID profileId, LocalDate activityDate, ActivitySource source, int minutes) {
        Key key = new Key(profileId, activityDate, source);
        DailyActivity previous = rows.get(key);
        int prev = previous == null ? 0 : previous.activeMinutes();
        DailyActivity row = new DailyActivity(profileId, activityDate, source, 0, prev + minutes);
        rows.put(key, row);
        return row;
    }

    @Override
    public ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to) {
        totalsCalls++;
        var inRange = rows.values().stream()
                .filter(it -> it.profileId().equals(profileId)
                        && !it.activityDate().isBefore(from)
                        && !it.activityDate().isAfter(to))
                .toList();
        return new ActivityTotals(
                inRange.stream().mapToInt(DailyActivity::steps).sum(),
                inRange.stream().mapToInt(DailyActivity::activeMinutes).sum(),
                inRange.stream()
                        .filter(it -> it.source().isServerVerified())
                        .mapToInt(DailyActivity::activeMinutes)
                        .sum());
    }

    @Override
    public int activeMinutesOn(UUID profileId, LocalDate activityDate) {
        return rows.values().stream()
                .filter(it ->
                        it.profileId().equals(profileId) && it.activityDate().equals(activityDate))
                .mapToInt(DailyActivity::activeMinutes)
                .sum();
    }

    @Override
    public List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to) {
        return verifiedByDay(profileId).entrySet().stream()
                .filter(it -> !it.getKey().isBefore(from) && !it.getKey().isAfter(to))
                .map(it -> new DailyMinutes(it.getKey(), it.getValue()))
                .toList();
    }

    @Override
    public VerifiedSummary verifiedSummary(UUID profileId) {
        Map<LocalDate, Integer> byDay = verifiedByDay(profileId);
        return new VerifiedSummary(
                byDay.size(),
                byDay.values().stream().mapToInt(Integer::intValue).sum());
    }

    /** 날짜마다 TIMER · VIDEO 분을 더해 0 보다 큰 날만. 날짜 오름차순. */
    private Map<LocalDate, Integer> verifiedByDay(UUID profileId) {
        Map<LocalDate, Integer> byDay = new TreeMap<>();
        rows.values().stream()
                .filter(it -> it.profileId().equals(profileId) && it.source().isServerVerified())
                .forEach(it -> byDay.merge(it.activityDate(), it.activeMinutes(), Integer::sum));
        byDay.values().removeIf(minutes -> minutes <= 0);
        return byDay;
    }
}
