package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.application.port.RestCardRepository;
import kr.ac.kookmin.familyfitness.activity.domain.RestCard;
import kr.ac.kookmin.familyfitness.shared.persistence.SqlErrors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class RestCardPersistenceAdapter implements RestCardRepository {
    private final RestCardJpaRepository jpa;

    public RestCardPersistenceAdapter(RestCardJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<RestCard> findByMonth(UUID familyId, YearMonth month) {
        return jpa.findByFamilyIdAndRestMonthOrderByRestDate(familyId, month.atDay(1)).stream()
                .map(RestCardPersistenceAdapter::toDomain)
                .toList();
    }

    /**
     * 곧바로 flush 해 유니크 위반을 여기서 받는다. 유니크 위반(SQLSTATE 23505)이면 false, FK · check 같은 다른 제약 위반은 그대로 던진다.
     * rest_cards 의 유니크는 두 인덱스(같은 날 · 같은 카드 번호)와 무작위 UUID 기본 키뿐이고, 어느 쪽이든 호출자는 새로 읽어 다시 검사하면
     * 되므로 인덱스 이름은 가르지 않는다. 위반 뒤 트랜잭션은 롤백 전용이 되므로(PostgreSQL 은 그 트랜잭션에서 더 읽지도 못한다)
     * 호출자는 그 트랜잭션을 끝내야 한다.
     */
    @Override
    @Transactional
    public boolean insert(RestCard card) {
        try {
            jpa.saveAndFlush(toEntity(card));
            return true;
        } catch (DataIntegrityViolationException e) {
            if (SqlErrors.isUniqueViolation(e)) return false;
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean delete(UUID familyId, LocalDate restDate) {
        return jpa.deleteByFamilyAndDate(familyId, restDate) > 0;
    }

    @Override
    public List<LocalDate> restDatesBetween(UUID familyId, LocalDate from, LocalDate to) {
        return jpa.restDatesBetween(familyId, from, to);
    }

    private static RestCard toDomain(RestCardEntity entity) {
        return new RestCard(
                entity.getId(),
                entity.getFamilyId(),
                entity.getRestDate(),
                entity.getCardNo(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }

    private static RestCardEntity toEntity(RestCard card) {
        return new RestCardEntity(
                card.id(),
                card.familyId(),
                card.restDate(),
                card.restMonth().atDay(1),
                card.cardNo(),
                card.createdBy(),
                card.createdAt());
    }
}
