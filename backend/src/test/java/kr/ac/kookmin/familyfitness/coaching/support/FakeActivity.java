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

/**
 * activity 모듈의 가짜: 일별·출처별 기록을 메모리에 쌓고 합계를 낸다. 실제 모듈처럼 시간은 초로 쌓고, 분은 초 합을 내림한다.
 * 「움직인 날」 은 서버가 잰 초(TIMER · VIDEO)가 0 보다 큰 날이다.
 */
public class FakeActivity implements ActivityRecorder, ActivityQuery {
    public record Key(UUID profileId, LocalDate date, ActivitySource source) {}

    public final Map<Key, DailyActivity> rows = new LinkedHashMap<>();

    /** 행마다 쌓인 초. */
    public final Map<Key, Integer> seconds = new LinkedHashMap<>();

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
        return addActiveSeconds(profileId, activityDate, source, minutes * 60);
    }

    @Override
    public DailyActivity addActiveSeconds(UUID profileId, LocalDate activityDate, ActivitySource source, int added) {
        Key key = new Key(profileId, activityDate, source);
        int total = seconds.merge(key, added, Integer::sum);
        DailyActivity row = new DailyActivity(profileId, activityDate, source, 0, total / 60);
        rows.put(key, row);
        return row;
    }

    @Override
    public ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to) {
        totalsCalls++;
        var inRange = rows.keySet().stream()
                .filter(it -> it.profileId().equals(profileId)
                        && !it.date().isBefore(from)
                        && !it.date().isAfter(to))
                .toList();
        return new ActivityTotals(
                inRange.stream().mapToInt(it -> rows.get(it).steps()).sum(),
                inRange.stream().mapToInt(this::secondsOf).sum() / 60,
                inRange.stream()
                                .filter(it -> it.source().isServerVerified())
                                .mapToInt(this::secondsOf)
                                .sum()
                        / 60);
    }

    @Override
    public int activeMinutesOn(UUID profileId, LocalDate activityDate) {
        return seconds.entrySet().stream()
                        .filter(it -> it.getKey().profileId().equals(profileId)
                                && it.getKey().date().equals(activityDate))
                        .mapToInt(Map.Entry::getValue)
                        .sum()
                / 60;
    }

    @Override
    public List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to) {
        return verifiedByDay(profileId).entrySet().stream()
                .filter(it -> !it.getKey().isBefore(from) && !it.getKey().isAfter(to))
                .map(it -> new DailyMinutes(it.getKey(), it.getValue() / 60))
                .toList();
    }

    @Override
    public VerifiedSummary verifiedSummary(UUID profileId) {
        Map<LocalDate, Integer> byDay = verifiedByDay(profileId);
        return new VerifiedSummary(
                byDay.size(),
                byDay.values().stream().mapToInt(Integer::intValue).sum() / 60);
    }

    private int secondsOf(Key key) {
        return seconds.getOrDefault(key, 0);
    }

    /** 날짜마다 TIMER · VIDEO 초를 더해 0 보다 큰 날만. 날짜 오름차순. */
    private Map<LocalDate, Integer> verifiedByDay(UUID profileId) {
        Map<LocalDate, Integer> byDay = new TreeMap<>();
        seconds.forEach((key, value) -> {
            if (key.profileId().equals(profileId) && key.source().isServerVerified()) {
                byDay.merge(key.date(), value, Integer::sum);
            }
        });
        byDay.values().removeIf(total -> total <= 0);
        return byDay;
    }
}
