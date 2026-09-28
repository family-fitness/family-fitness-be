package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class CheerRepositoryAdapter implements CheerRepository {
    /** THANKS 한 스티커에 한 번(V136). */
    private static final String REPLY_INDEX = "ux_cheers_reply_to";

    private final CheerJpaRepository jpa;
    private final EntityManager em;

    public CheerRepositoryAdapter(CheerJpaRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    /**
     * persist 뒤 곧바로 flush 해 유니크 인덱스 위반을 여기서 받는다(flush 는 Spring Data 프록시를 거쳐 예외가 번역된다).
     * 위반 뒤 트랜잭션은 롤백 전용이 되므로 예외를 던져 끝낸다.
     */
    @Override
    public Cheer save(Cheer cheer) {
        em.persist(toEntity(cheer));
        try {
            jpa.flush();
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(REPLY_INDEX)) {
                throw new AlreadyThankedException();
            }
            throw e;
        }
        return cheer;
    }

    @Override
    public @Nullable Cheer findById(UUID cheerId) {
        return jpa.findById(cheerId).map(CheerRepositoryAdapter::toDomain).orElse(null);
    }

    @Override
    public boolean existsReplyTo(UUID cheerId) {
        return jpa.existsByReplyToCheerId(cheerId);
    }

    @Override
    public int countFromTo(UUID fromProfileId, UUID toProfileId, Instant after) {
        return (int) jpa.countByFromProfileIdAndToProfileIdAndCreatedAtAfter(fromProfileId, toProfileId, after);
    }

    @Override
    public int countInFamily(UUID familyId, Instant from, Instant to) {
        return (int) jpa.countByFamilyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(familyId, from, to);
    }

    /** 거르기 값이 있는 것만 where 에 붙인다(null 파라미터를 PostgreSQL 이 형을 못 정하는 문제를 피한다). */
    @Override
    public List<Cheer> findInFamily(
            UUID familyId,
            @Nullable UUID toProfileId,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            int limit) {
        StringBuilder jpql = new StringBuilder("select c from CheerEntity c where c.familyId = :familyId");
        if (toProfileId != null) jpql.append(" and c.toProfileId = :toProfileId");
        if (fromProfileId != null) jpql.append(" and c.fromProfileId = :fromProfileId");
        if (missionId != null) jpql.append(" and c.missionId = :missionId");
        jpql.append(" order by c.createdAt desc, c.id desc");
        TypedQuery<CheerEntity> query = em.createQuery(jpql.toString(), CheerEntity.class)
                .setParameter("familyId", familyId)
                .setMaxResults(limit);
        if (toProfileId != null) query.setParameter("toProfileId", toProfileId);
        if (fromProfileId != null) query.setParameter("fromProfileId", fromProfileId);
        if (missionId != null) query.setParameter("missionId", missionId);
        return query.getResultList().stream()
                .map(CheerRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Cheer> findReceived(UUID toProfileId, Instant from, Instant to) {
        return jpa
                .findByToProfileIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        toProfileId, from, to)
                .stream()
                .map(CheerRepositoryAdapter::toDomain)
                .toList();
    }

    private static CheerEntity toEntity(Cheer cheer) {
        return new CheerEntity(
                cheer.id(),
                cheer.familyId(),
                cheer.fromProfileId(),
                cheer.toProfileId(),
                cheer.kind().name(),
                cheer.missionId(),
                cheer.stickerId(),
                cheer.message(),
                cheer.replyToCheerId(),
                cheer.createdAt());
    }

    private static Cheer toDomain(CheerEntity entity) {
        return new Cheer(
                entity.getId(),
                entity.getFamilyId(),
                entity.getFromProfileId(),
                entity.getToProfileId(),
                CheerKind.valueOf(entity.getKind()),
                entity.getMessage(),
                entity.getStickerId(),
                entity.getMissionId(),
                entity.getReplyToCheerId(),
                entity.getCreatedAt());
    }
}
