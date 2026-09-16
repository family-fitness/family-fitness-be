package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity;

/** activity 모듈의 가짜: 일별·출처별 기록을 메모리에 쌓고 합계를 낸다. */
public class FakeActivity implements ActivityRecorder, ActivityQuery {
    public record Key(UUID profileId, LocalDate date, ActivitySource source) {}

    public final Map<Key, DailyActivity> rows = new LinkedHashMap<>();

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
}
