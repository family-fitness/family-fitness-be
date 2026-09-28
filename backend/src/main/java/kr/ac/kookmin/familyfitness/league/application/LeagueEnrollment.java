package kr.ac.kookmin.familyfitness.league.application;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.LeagueBusyException;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTable;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가족을 그달 방에 넣는다. 조회(첫 리그 조회)와 월초 정산이 부른다. 이미 방에 있으면 그 자리를 돌려준다.
 *
 * <pre>
 * 티어   리그에 처음이면 브론즈. 아니면 가장 최근에 있던 달의 티어에 그달 정산 결과를 더한다
 *        — 그달이 아직 정산 전이면(1일 00:10 전 조회 · 서버가 꺼져 있던 달) 여기서 먼저 정산한다
 * 방     그 티어의 그달 방 가운데 열 가족이 안 찬 첫 방(방 번호 차례). 다 찼으면 새 방
 * 자리   그 방의 마지막 자리 번호 + 1
 * </pre>
 *
 * 두 요청이 같은 방 · 같은 자리를 잡거나 같은 가족을 두 번 넣으면 늦은 쪽의 insert 가 유니크 인덱스에 걸린다.
 * 그때는 그 트랜잭션을 되돌리고 새로 읽어 다시 고른다.
 */
@Component
public class LeagueEnrollment {
    /** 다른 요청에 자리를 뺏겼을 때 다시 해 보는 횟수(첫 시도 포함) */
    static final int MAX_ATTEMPTS = 5;

    private final LeagueRepository repository;
    private final LeagueSettlement settlement;
    private final TransactionTemplate tx;
    private final Clock clock;

    public LeagueEnrollment(
            LeagueRepository repository, LeagueSettlement settlement, TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.settlement = settlement;
        this.tx = tx;
        this.clock = clock;
    }

    /** 이 가족의 그달 자리. 없으면 만든다. */
    public LeagueMember ensureMember(UUID familyId, YearMonth month) {
        LeagueMember existing = repository.findMember(familyId, month);
        if (existing != null) return existing;
        LeagueTier tier = tierFor(familyId, month);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LeagueMember joined = tx.execute(status -> tryJoin(familyId, month, tier, status));
            if (joined != null) return joined;
        }
        throw new LeagueBusyException();
    }

    /** 그달 들어갈 티어. 가장 최근에 있던 달이 정산 전이면 먼저 정산한다. */
    LeagueTier tierFor(UUID familyId, YearMonth month) {
        LeagueMember last = repository.findLatestMemberBefore(familyId, month);
        if (last == null) return LeagueTier.START;
        LeagueRound round = settlement.settle(last.roundId());
        LeagueMember settled = repository.findMember(familyId, last.month());
        return (settled != null ? settled : last).nextTier(round.tier());
    }

    /** 한 번 넣어 본다. 다른 요청에 자리를 뺏겼으면 이 트랜잭션을 되돌리고 null — 다음 시도가 새로 읽는다. */
    private @Nullable LeagueMember tryJoin(UUID familyId, YearMonth month, LeagueTier tier, TransactionStatus status) {
        LeagueMember existing = repository.findMember(familyId, month);
        if (existing != null) return existing;
        Instant now = Instant.now(clock);
        List<LeagueRound> rounds = repository.roundsOf(month, tier);
        for (LeagueRound round : rounds) {
            List<LeagueMember> seats = repository.membersOf(round.id());
            if (seats.size() >= LeagueTable.GROUP_SIZE) continue;
            int seatNo = seats.stream().mapToInt(LeagueMember::seatNo).max().orElse(0) + 1;
            if (seatNo > LeagueTable.GROUP_SIZE) continue;
            return insert(LeagueMember.join(round, familyId, seatNo, now), status);
        }
        int groupNo = rounds.stream().mapToInt(LeagueRound::groupNo).max().orElse(0) + 1;
        LeagueRound opened = LeagueRound.open(month, tier, groupNo, now);
        if (!repository.insertRound(opened)) {
            status.setRollbackOnly();
            return null;
        }
        return insert(LeagueMember.join(opened, familyId, 1, now), status);
    }

    private @Nullable LeagueMember insert(LeagueMember member, TransactionStatus status) {
        if (repository.insertMember(member)) return member;
        status.setRollbackOnly();
        return null;
    }
}
