package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** 원장 읽기 · 넣기. 고치거나 지우는 쿼리를 두지 않는다. */
public interface XpEventJpaRepository extends JpaRepository<XpEventEntity, UUID> {
    /**
     * 같은 (profile_id, kind, source_key) 가 이미 있으면 아무것도 하지 않고 0 을 돌려준다(uq_progress_xp_events_source).
     * 먼저 읽고 넣으면 동시에 온 두 요청이 함께 「없음」 을 보고, 늦은 쪽이 커밋 때 유니크 위반으로 응원 · 칸 끝까지 통째로 되돌린다
     * (QA SA-12). 충돌은 DB 가 삼키게 한다 — 앞 트랜잭션이 아직 커밋 전이면 끝나길 기다렸다가 판단한다.
     * ON CONFLICT DO NOTHING 은 PostgreSQL 과 H2(MODE=PostgreSQL) 둘 다 받는다. 영속성 컨텍스트는 비우지 않는다 — 같은 트랜잭션의
     * 칸 끝 · 응원 · 측정이 붙들고 있는 엔티티를 떼어 내지 않게(새 행만 넣으므로 비울 까닭도 없다).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into progress_xp_events
                (id, profile_id, kind, source_key, amount, from_profile_id, mission_id, phase, factor, occurred_on, created_at)
            values
                (:id, :profileId, :kind, :sourceKey, :amount, :fromProfileId, :missionId, :phase, :factor, :occurredOn,
                 :createdAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            UUID id,
            UUID profileId,
            String kind,
            String sourceKey,
            int amount,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            @Nullable String phase,
            @Nullable String factor,
            LocalDate occurredOn,
            Instant createdAt);

    @Query("select coalesce(sum(e.amount), 0) from XpEventEntity e where e.profileId = :profileId")
    long sumAmount(UUID profileId);

    List<XpEventEntity> findByProfileIdOrderByCreatedAtDescIdDesc(UUID profileId, Limit limit);

    List<XpEventEntity> findByProfileIdAndKindInAndOccurredOnIn(
            UUID profileId, Collection<String> kinds, Collection<LocalDate> dates);
}
