package kr.ac.kookmin.familyfitness.league.application.port;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMove;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import org.jspecify.annotations.Nullable;

/** 리그 방 · 자리 저장소(league_rounds · league_members). */
public interface LeagueRepository {
    @Nullable
    LeagueRound findRound(UUID roundId);

    /** 그달 그 티어의 방. 방 번호 차례. */
    List<LeagueRound> roundsOf(YearMonth month, LeagueTier tier);

    /** 그달 정산이 안 끝난 방. 티어 · 방 번호 차례. */
    List<LeagueRound> unsettledRoundsOf(YearMonth month);

    @Nullable
    LeagueMember findMember(UUID familyId, YearMonth month);

    /** {@code month} 앞의 달 가운데 이 가족이 방에 있던 가장 최근 달의 자리. */
    @Nullable
    LeagueMember findLatestMemberBefore(UUID familyId, YearMonth month);

    /** 방의 자리. 자리 번호 차례. */
    List<LeagueMember> membersOf(UUID roundId);

    /** 그달 모든 자리. 들어온 시각 차례(같으면 가족 id 차례). */
    List<LeagueMember> membersOfMonth(YearMonth month);

    /** 새 방. 같은 (달, 티어, 방 번호)가 이미 있으면(다른 요청이 먼저 만들었으면) false — 트랜잭션은 롤백 전용이 된다. */
    boolean insertRound(LeagueRound round);

    /** 새 자리. 그달 이미 방에 있는 가족이거나 같은 자리 번호가 찼으면 false — 트랜잭션은 롤백 전용이 된다. */
    boolean insertMember(LeagueMember member);

    /** 정산이 안 끝난 방이면 끝난 것으로 적고 true. 이미 끝났으면(다른 실행이 먼저 했으면) false. */
    boolean markSettled(UUID roundId, Instant at);

    /** 정산 결과를 자리에 적는다. */
    void saveResult(
            UUID roundId,
            UUID familyId,
            @Nullable Integer finalRate,
            @Nullable Double finalScore,
            @Nullable Integer finalRank,
            LeagueMove moved);
}
