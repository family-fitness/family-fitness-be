package kr.ac.kookmin.familyfitness.league.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 한 달 · 한 티어의 방(묶음) 하나.
 *
 * @param groupNo 그달 그 티어 안의 방 번호(1부터)
 * @param settledAt 정산이 끝난 시각. 아직이면 null — 정산은 방마다 한 번뿐이다
 */
public record LeagueRound(
        UUID id,
        YearMonth month,
        LeagueTier tier,
        int groupNo,
        Instant createdAt,
        @Nullable Instant settledAt) {
    public LeagueRound {
        if (groupNo < 1) throw new IllegalArgumentException("방 번호(groupNo)는 1부터다");
    }

    public static LeagueRound open(YearMonth month, LeagueTier tier, int groupNo, Instant at) {
        return new LeagueRound(UUID.randomUUID(), month, tier, groupNo, at, null);
    }

    public boolean isSettled() {
        return settledAt != null;
    }
}
