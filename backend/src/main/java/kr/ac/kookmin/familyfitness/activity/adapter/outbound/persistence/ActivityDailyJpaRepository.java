package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityDailyJpaRepository extends JpaRepository<ActivityDailyEntity, UUID> {
    @Nullable
    ActivityDailyEntity findByProfileIdAndActivityDateAndSource(UUID profileId, LocalDate activityDate, String source);

    /** 기간 합계 — 초로 더한다(분 변환은 부르는 쪽이 합에서 한 번만 한다). */
    @Query("""
            select new kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence.ActivitySums(
                sum(a.steps),
                sum(a.activeSeconds),
                sum(case when a.source in :verifiedSources then a.activeSeconds else 0 end))
            from ActivityDailyEntity a
            where a.profileId = :profileId and a.activityDate between :from and :to
            """)
    ActivitySums sumBetween(UUID profileId, LocalDate from, LocalDate to, Collection<String> verifiedSources);

    @Query(
            "select coalesce(sum(a.activeSeconds), 0) from ActivityDailyEntity a where a.profileId = :profileId and a.activityDate = :date")
    long sumActiveSecondsOn(UUID profileId, LocalDate date);

    /** 그날 누구라도 초가 있는가(움직인 날). MANUAL(걸음수) 행은 초가 늘 0 이다. */
    boolean existsByProfileIdInAndActivityDateAndActiveSecondsGreaterThan(
            Collection<UUID> profileIds, LocalDate activityDate, int activeSeconds);

    /** 날짜마다 출처 행(TIMER · VIDEO)의 초를 더해 0 보다 큰 날만 남긴다. */
    @Query("""
            select new kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence.DayMinutesRow(
                a.activityDate, sum(a.activeSeconds))
            from ActivityDailyEntity a
            where a.profileId = :profileId and a.activityDate between :from and :to and a.source in :sources
            group by a.activityDate
            having sum(a.activeSeconds) > 0
            order by a.activityDate
            """)
    List<DayMinutesRow> sumByDay(UUID profileId, LocalDate from, LocalDate to, Collection<String> sources);

    @Query("""
            select count(distinct a.activityDate) from ActivityDailyEntity a
            where a.profileId = :profileId and a.source in :sources and a.activeSeconds > 0
            """)
    long countActiveDays(UUID profileId, Collection<String> sources);

    @Query("""
            select coalesce(sum(a.activeSeconds), 0) from ActivityDailyEntity a
            where a.profileId = :profileId and a.source in :sources
            """)
    long sumSeconds(UUID profileId, Collection<String> sources);
}
