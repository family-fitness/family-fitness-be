package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 원장 읽기 · 넣기. 고치거나 지우는 쿼리를 두지 않는다. */
public interface XpEventJpaRepository extends JpaRepository<XpEventEntity, UUID> {
    boolean existsByProfileIdAndKindAndSourceKey(UUID profileId, String kind, String sourceKey);

    @Query("select coalesce(sum(e.amount), 0) from XpEventEntity e where e.profileId = :profileId")
    long sumAmount(UUID profileId);

    List<XpEventEntity> findByProfileIdOrderByCreatedAtDescIdDesc(UUID profileId, Limit limit);

    List<XpEventEntity> findByProfileIdAndKindInAndOccurredOnIn(
            UUID profileId, Collection<String> kinds, Collection<LocalDate> dates);
}
