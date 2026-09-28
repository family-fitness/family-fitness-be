package kr.ac.kookmin.familyfitness.league.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 한 가족의 한 달 자리. 한 가족은 한 달에 한 방에만 있다.
 *
 * @param seatNo 방에 들어온 차례(1부터)
 * @param finalRate 정산 때 굳힌 달성률(%). 정산 전이거나 셀 날이 없었으면 null
 * @param finalRank 정산 때 굳힌 순위. 정산 전이거나 달성률이 없었으면 null
 * @param moved 정산 때 정한 가는 곳. 정산 전이면 null
 */
public record LeagueMember(
        UUID roundId,
        UUID familyId,
        YearMonth month,
        int seatNo,
        Instant joinedAt,
        @Nullable Integer finalRate,
        @Nullable Integer finalRank,
        @Nullable LeagueMove moved) {
    public LeagueMember {
        if (seatNo < 1 || seatNo > LeagueTable.GROUP_SIZE) {
            throw new IllegalArgumentException("자리 번호(seatNo)는 1~" + LeagueTable.GROUP_SIZE + " 이다: " + seatNo);
        }
    }

    /** 방에 새로 들어온다. 결과 칸은 정산 때 채운다. */
    public static LeagueMember join(LeagueRound round, UUID familyId, int seatNo, Instant at) {
        return new LeagueMember(round.id(), familyId, round.month(), seatNo, at, null, null, null);
    }

    /** 다음 달의 티어 — 이 달 방의 티어에 정산 결과를 더한다. 정산 전이면 그대로다. */
    public LeagueTier nextTier(LeagueTier current) {
        return current.after(moved != null ? moved : LeagueMove.STAY);
    }
}
