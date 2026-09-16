package kr.ac.kookmin.familyfitness.activity.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity;

/**
 * 한 프로필의 (날짜, 출처) 당 한 행. 걸음수는 그날 총량으로 덮어쓰고(MANUAL), 분은 누적한다(TIMER·VIDEO).
 * 규칙: 걸음 0~100000 · 분 &gt; 0 · 분의 출처는 서버가 아는 TIMER·VIDEO 만.
 */
public class DailyActivityRecord {
    public static final int MAX_STEPS = 100_000;

    private final UUID id;
    private final UUID profileId;
    private final LocalDate activityDate;
    private final ActivitySource source;
    private int steps;
    private int activeMinutes;
    private Instant recordedAt;

    public DailyActivityRecord(
            UUID id,
            UUID profileId,
            LocalDate activityDate,
            ActivitySource source,
            int steps,
            int activeMinutes,
            Instant recordedAt) {
        this.id = id;
        this.profileId = profileId;
        this.activityDate = activityDate;
        this.source = source;
        this.steps = steps;
        this.activeMinutes = activeMinutes;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public ActivitySource getSource() {
        return source;
    }

    public int getSteps() {
        return steps;
    }

    public int getActiveMinutes() {
        return activeMinutes;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void overwriteSteps(int steps, Instant at) {
        validateSteps(steps);
        this.steps = steps;
        this.recordedAt = at;
    }

    public void addActiveMinutes(int minutes, Instant at) {
        validateMinutes(minutes);
        this.activeMinutes += minutes;
        this.recordedAt = at;
    }

    public DailyActivity toDailyActivity() {
        return new DailyActivity(profileId, activityDate, source, steps, activeMinutes);
    }

    public static DailyActivityRecord newSteps(UUID profileId, LocalDate activityDate, int steps, Instant at) {
        validateSteps(steps);
        return new DailyActivityRecord(UUID.randomUUID(), profileId, activityDate, ActivitySource.MANUAL, steps, 0, at);
    }

    public static DailyActivityRecord newMinutes(
            UUID profileId, LocalDate activityDate, ActivitySource source, int minutes, Instant at) {
        validateMinuteSource(source);
        validateMinutes(minutes);
        return new DailyActivityRecord(UUID.randomUUID(), profileId, activityDate, source, 0, minutes, at);
    }

    public static void validateSteps(int steps) {
        if (steps < 0 || steps > MAX_STEPS) {
            throw new IllegalArgumentException("걸음수는 0~" + MAX_STEPS + " 사이여야 합니다: " + steps);
        }
    }

    public static void validateMinutes(int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("활동 분은 0보다 커야 합니다: " + minutes);
    }

    public static void validateMinuteSource(ActivitySource source) {
        if (!source.isServerVerified()) {
            throw new IllegalArgumentException("활동 분의 출처는 TIMER·VIDEO 만 가능합니다: " + source);
        }
    }
}
