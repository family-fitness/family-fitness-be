package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityDailyJpaRepository extends JpaRepository<ActivityDailyEntity, UUID> {
    @Nullable
    ActivityDailyEntity findByProfileIdAndActivityDateAndSource(UUID profileId, LocalDate activityDate, String source);

    @Query("""
            select new kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence.ActivitySums(
                sum(a.steps),
                sum(a.activeMinutes),
                sum(case when a.source in :verifiedSources then a.activeMinutes else 0 end))
            from ActivityDailyEntity a
            where a.profileId = :profileId and a.activityDate between :from and :to
            """)
    ActivitySums sumBetween(UUID profileId, LocalDate from, LocalDate to, Collection<String> verifiedSources);

    @Query(
            "select coalesce(sum(a.activeMinutes), 0) from ActivityDailyEntity a where a.profileId = :profileId and a.activityDate = :date")
    long sumActiveMinutesOn(UUID profileId, LocalDate date);

    boolean existsByProfileIdInAndActivityDateAndActiveMinutesGreaterThan(
            Collection<UUID> profileIds, LocalDate activityDate, int activeMinutes);
}
