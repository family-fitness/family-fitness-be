package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** {@link SessionCompletionRepository} 의 JPA 구현. */
@Repository
public class SessionCompletionPersistenceAdapter implements SessionCompletionRepository {
    private final MissionSessionCompletionJpaRepository jpa;
    private final EntityManager em;

    public SessionCompletionPersistenceAdapter(MissionSessionCompletionJpaRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public @Nullable SessionCompletion find(UUID missionId, int position, UUID profileId) {
        return jpa.findById(new MissionSessionCompletionId(missionId, position, profileId))
                .map(SessionCompletionPersistenceAdapter::toDomain)
                .orElse(null);
    }

    /**
     * persist 로 넣고 곧바로 내보낸다. 키를 채운 엔티티를 save 로 넘기면 merge(읽고 있으면 UPDATE)가 되어, 동시에 먼저 들어간
     * 행을 덮어쓸 수 있다. 같은 칸 요청 둘이 동시에 오면 늦은 쪽이 여기서 기본 키에 걸린다(커밋 때가 아니라) —
     * {@code @Repository} 라 스프링이 DataIntegrityViolationException 으로 바꿔 던진다.
     */
    @Override
    public void insert(SessionCompletion completion) {
        em.persist(new MissionSessionCompletionEntity(
                new MissionSessionCompletionId(completion.missionId(), completion.position(), completion.profileId()),
                completion.completedAt(),
                completion.completedOn(),
                completion.activeSeconds(),
                completion.verifiedBy().name()));
        em.flush();
    }

    @Override
    public List<SessionCompletion> findByMission(UUID missionId) {
        return jpa.findByIdMissionId(missionId).stream()
                .map(SessionCompletionPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<SessionCompletion> findByMissions(Collection<UUID> missionIds) {
        if (missionIds.isEmpty()) return List.of();
        return jpa.findByIdMissionIdIn(missionIds).stream()
                .map(SessionCompletionPersistenceAdapter::toDomain)
                .toList();
    }

    static SessionCompletion toDomain(MissionSessionCompletionEntity e) {
        return new SessionCompletion(
                e.getId().getMissionId(),
                e.getId().getPosition(),
                e.getId().getProfileId(),
                e.getCompletedAt(),
                e.getCompletedOn(),
                e.getActiveSeconds(),
                VerifiedBy.valueOf(e.getVerifiedBy()));
    }
}
