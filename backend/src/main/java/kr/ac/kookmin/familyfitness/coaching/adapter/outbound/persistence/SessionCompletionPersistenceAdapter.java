package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.shared.persistence.SqlErrors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** {@link SessionCompletionRepository} 의 JPA 구현. */
@Repository
public class SessionCompletionPersistenceAdapter implements SessionCompletionRepository {
    /** V144 의 칸 끝 → 미션 외래 키. 위반 메시지에 이 이름이 실린다(PostgreSQL · H2 모두, 서버 언어와 상관없이). */
    private static final String MISSION_FK = "fk_mission_session_completions_mission";

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
     * 행을 덮어쓸 수 있다. 내보내기는 Spring Data 프록시의 flush 를 거쳐 DataIntegrityViolationException 으로 번역된다.
     * <ul>
     *   <li>같은 칸 요청 둘이 동시에 오면 늦은 쪽이 기본 키에 걸린다(커밋 때가 아니라 여기서). 그대로 던진다 — 컨트롤러가 한 번 더 부른다.
     *   <li>미션 외래 키에 걸리면 부르는 쪽이 미션을 읽은 뒤 그 미션이 지워진 것이다. 칸 끝은 미션 행을 먼저 잠그고 넣으므로(지우기와
     *       같은 잠금) 보통은 생기지 않지만, 생기면 서버 버그가 아니라 없는 미션이라 404 로 바꾼다.
     * </ul>
     * 위반 뒤 트랜잭션은 롤백 전용이 되므로 예외로 끝낸다.
     */
    @Override
    public void insert(SessionCompletion completion) {
        em.persist(new MissionSessionCompletionEntity(
                new MissionSessionCompletionId(completion.missionId(), completion.position(), completion.profileId()),
                completion.completedAt(),
                completion.completedOn(),
                completion.activeSeconds(),
                completion.verifiedBy().name()));
        try {
            jpa.flush();
        } catch (DataIntegrityViolationException e) {
            if (isMissionGone(e)) throw new MissionNotFoundException(completion.missionId());
            throw e;
        }
    }

    private static boolean isMissionGone(DataIntegrityViolationException e) {
        if (!SqlErrors.isForeignKeyViolation(e)) return false;
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(MISSION_FK);
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
