package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.VerifiedSummary;
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository;
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ActivityDailyRepositoryAdapter implements ActivityDailyRepository {
    private final ActivityDailyJpaRepository jpa;
    private final List<String> verifiedSources = Arrays.stream(ActivitySource.values())
            .filter(ActivitySource::isServerVerified)
            .map(Enum::name)
            .toList();

    public ActivityDailyRepositoryAdapter(ActivityDailyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public @Nullable DailyActivityRecord find(UUID profileId, LocalDate activityDate, ActivitySource source) {
        ActivityDailyEntity entity =
                jpa.findByProfileIdAndActivityDateAndSource(profileId, activityDate, source.name());
        return entity == null ? null : toDomain(entity);
    }

    @Override
    @Transactional
    public DailyActivityRecord save(DailyActivityRecord record) {
        ActivityDailyEntity existing = jpa.findById(record.getId()).orElse(null);
        ActivityDailyEntity entity;
        if (existing == null) {
            entity = toEntity(record);
        } else {
            existing.setSteps(record.getSteps());
            existing.setActiveSeconds(record.getActiveSeconds());
            existing.setRecordedAt(record.getRecordedAt());
            entity = existing;
        }
        jpa.save(entity);
        return record;
    }

    @Override
    public ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to) {
        ActivitySums sums = jpa.sumBetween(profileId, from, to, verifiedSources);
        return new ActivityTotals(
                (int) orZero(sums.steps()),
                minutesOf(orZero(sums.activeSeconds())),
                minutesOf(orZero(sums.verifiedSeconds())));
    }

    @Override
    public int activeMinutesOn(UUID profileId, LocalDate activityDate) {
        return minutesOf(jpa.sumActiveSecondsOn(profileId, activityDate));
    }

    @Override
    public boolean anyActiveOn(Collection<UUID> profileIds, LocalDate activityDate) {
        if (profileIds.isEmpty()) return false;
        return jpa.existsByProfileIdInAndActivityDateAndActiveSecondsGreaterThan(profileIds, activityDate, 0);
    }

    @Override
    public List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to) {
        return jpa.sumByDay(profileId, from, to, verifiedSources).stream()
                .map(it -> new DailyMinutes(it.date(), minutesOf(it.seconds())))
                .toList();
    }

    @Override
    public VerifiedSummary verifiedSummary(UUID profileId) {
        return new VerifiedSummary(
                (int) jpa.countActiveDays(profileId, verifiedSources),
                minutesOf(jpa.sumSeconds(profileId, verifiedSources)));
    }

    private static long orZero(@Nullable Long value) {
        return value == null ? 0L : value;
    }

    /** 초 합 → 분(내림). 칸마다 나눠 올리지 않고 합에서 한 번만 바꾼다(결정 37). */
    private static int minutesOf(long seconds) {
        return Math.toIntExact(seconds / 60);
    }

    private static DailyActivityRecord toDomain(ActivityDailyEntity entity) {
        return new DailyActivityRecord(
                entity.getId(),
                entity.getProfileId(),
                entity.getActivityDate(),
                ActivitySource.valueOf(entity.getSource()),
                entity.getSteps(),
                entity.getActiveSeconds(),
                entity.getRecordedAt());
    }

    private static ActivityDailyEntity toEntity(DailyActivityRecord record) {
        return new ActivityDailyEntity(
                record.getId(),
                record.getProfileId(),
                record.getActivityDate(),
                record.getSource().name(),
                record.getSteps(),
                record.getActiveSeconds(),
                record.getRecordedAt());
    }
}
