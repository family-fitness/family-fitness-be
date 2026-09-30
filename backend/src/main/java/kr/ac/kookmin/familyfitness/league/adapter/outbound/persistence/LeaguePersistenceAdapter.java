package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMove;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import kr.ac.kookmin.familyfitness.shared.persistence.SqlErrors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class LeaguePersistenceAdapter implements LeagueRepository {
    private final LeagueRoundJpaRepository rounds;
    private final LeagueMemberJpaRepository members;

    public LeaguePersistenceAdapter(LeagueRoundJpaRepository rounds, LeagueMemberJpaRepository members) {
        this.rounds = rounds;
        this.members = members;
    }

    @Override
    public @Nullable LeagueRound findRound(UUID roundId) {
        return rounds.findById(roundId).map(LeaguePersistenceAdapter::toDomain).orElse(null);
    }

    @Override
    public List<LeagueRound> roundsOf(YearMonth month, LeagueTier tier) {
        return rounds.findByRoundMonthAndTierOrderByGroupNo(month.atDay(1), tier.name()).stream()
                .map(LeaguePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<LeagueRound> unsettledRoundsOf(YearMonth month) {
        return rounds.findByRoundMonthAndSettledAtIsNullOrderByTierAscGroupNoAsc(month.atDay(1)).stream()
                .map(LeaguePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public @Nullable LeagueMember findMember(UUID familyId, YearMonth month) {
        return members.findByIdFamilyIdAndRoundMonth(familyId, month.atDay(1))
                .map(LeaguePersistenceAdapter::toDomain)
                .orElse(null);
    }

    @Override
    public @Nullable LeagueMember findLatestMemberBefore(UUID familyId, YearMonth month) {
        return members.findFirstByIdFamilyIdAndRoundMonthLessThanOrderByRoundMonthDesc(familyId, month.atDay(1))
                .map(LeaguePersistenceAdapter::toDomain)
                .orElse(null);
    }

    @Override
    public List<LeagueMember> membersOf(UUID roundId) {
        return members.findByIdRoundIdOrderBySeatNo(roundId).stream()
                .map(LeaguePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<LeagueMember> membersOfMonth(YearMonth month) {
        return members.findByRoundMonthOrderByJoinedAtAscIdFamilyIdAsc(month.atDay(1)).stream()
                .map(LeaguePersistenceAdapter::toDomain)
                .toList();
    }

    /**
     * 곧바로 flush 해 유니크 위반을 여기서 받는다(SQLSTATE 23505 이면 false, 다른 제약 위반은 그대로 던진다).
     * 위반 뒤 트랜잭션은 롤백 전용이 되므로(PostgreSQL 은 그 트랜잭션에서 더 읽지도 못한다) 호출자는 그 트랜잭션을 끝내야 한다.
     */
    @Override
    @Transactional
    public boolean insertRound(LeagueRound round) {
        try {
            rounds.saveAndFlush(new LeagueRoundEntity(
                    round.id(), round.month().atDay(1), round.tier().name(), round.groupNo(), round.createdAt()));
            return true;
        } catch (DataIntegrityViolationException e) {
            if (SqlErrors.isUniqueViolation(e)) return false;
            throw e;
        }
    }

    /**
     * {@link #insertRound} 와 같다. 기본 키(방 · 가족) · 가족 · 달 유니크 · 방 · 자리 번호 유니크 어느 쪽이든 호출자는 새로 읽어 다시 고른다.
     * 엔티티가 새 행임을 알리므로({@link LeagueMemberEntity#isNew}) 같은 (방, 가족) 행이 이미 있어도 덮어쓰지 않고 기본 키 위반이 난다.
     */
    @Override
    @Transactional
    public boolean insertMember(LeagueMember member) {
        try {
            members.saveAndFlush(new LeagueMemberEntity(
                    new LeagueMemberId(member.roundId(), member.familyId()),
                    member.month().atDay(1),
                    member.seatNo(),
                    member.joinedAt()));
            return true;
        } catch (DataIntegrityViolationException e) {
            if (SqlErrors.isUniqueViolation(e)) return false;
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean markSettled(UUID roundId, Instant at) {
        return rounds.markSettled(roundId, at) > 0;
    }

    @Override
    @Transactional
    public void saveResult(
            UUID roundId,
            UUID familyId,
            @Nullable Integer finalRate,
            @Nullable Double finalScore,
            @Nullable Integer finalRank,
            LeagueMove moved) {
        if (members.saveResult(roundId, familyId, finalRate, finalScore, finalRank, moved.name()) != 1) {
            throw new IllegalStateException("정산 결과를 적을 자리가 없습니다: " + roundId + " / " + familyId);
        }
    }

    private static LeagueRound toDomain(LeagueRoundEntity entity) {
        return new LeagueRound(
                entity.getId(),
                YearMonth.from(entity.getRoundMonth()),
                LeagueTier.valueOf(entity.getTier()),
                entity.getGroupNo(),
                entity.getCreatedAt(),
                entity.getSettledAt());
    }

    private static LeagueMember toDomain(LeagueMemberEntity entity) {
        String moved = entity.getMoved();
        return new LeagueMember(
                entity.getId().getRoundId(),
                entity.getId().getFamilyId(),
                YearMonth.from(entity.getRoundMonth()),
                entity.getSeatNo(),
                entity.getJoinedAt(),
                entity.getFinalRate(),
                entity.getFinalScore(),
                entity.getFinalRank(),
                moved == null ? null : LeagueMove.valueOf(moved));
    }
}
