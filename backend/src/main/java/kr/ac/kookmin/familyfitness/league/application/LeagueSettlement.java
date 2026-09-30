package kr.ac.kookmin.familyfitness.league.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate.Result;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방 하나의 정산 — 끝난 달의 달성률 · 순위 점수를 말일까지 세어 굳히고, 순위와 가는 곳({@link LeagueTable#moveOf})을 적는다.
 * 방마다 한 번뿐이다. 먼저 {@code settled_at} 을 조건부 UPDATE 로 채운 실행만 결과를 적으므로, 스케줄러가 두 번 돌거나
 * 서버 여러 대 · 조회가 동시에 불러도 결과가 두 번 적히지 않는다(늦은 쪽은 0 행을 받고 아무것도 적지 않는다).
 */
@Component
public class LeagueSettlement {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final LeagueRepository repository;
    private final FamilyRates rates;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public LeagueSettlement(
            LeagueRepository repository, FamilyRates rates, TransactionTemplate tx, Clock clock, ZoneId appZone) {
        this.repository = repository;
        this.rates = rates;
        this.tx = tx;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 방을 정산하고 정산 뒤의 방을 돌려준다. 이미 끝난 방이면 그대로 돌려준다. 아직 끝나지 않은 달이면 IllegalStateException. */
    public LeagueRound settle(UUID roundId) {
        LeagueRound round = requireRound(roundId);
        if (round.isSettled()) return round;
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        if (!round.month().isBefore(YearMonth.from(today))) {
            throw new IllegalStateException("끝나지 않은 달의 방은 정산하지 않습니다: " + round.month());
        }
        Boolean settledHere = tx.execute(status -> {
            List<LeagueMember> members = repository.membersOf(roundId);
            List<UUID> familyIds = members.stream().map(LeagueMember::familyId).toList();
            Map<UUID, @Nullable Result> finalRates = rates.of(familyIds, round.month(), today);
            LeagueTable table = new LeagueTable(
                    round.tier(),
                    members.stream()
                            .map(it -> LeagueService.seat(it, finalRates.get(it.familyId())))
                            .toList());
            // 다른 실행이 먼저 끝냈다 — 여기까지는 읽기만 했으니 적을 것 없이 나간다
            if (!repository.markSettled(roundId, Instant.now(clock))) return false;
            for (UUID familyId : familyIds) {
                Result result = finalRates.get(familyId);
                repository.saveResult(
                        roundId,
                        familyId,
                        result != null ? result.rate() : null,
                        result != null ? result.score() : null,
                        table.rankOf(familyId),
                        table.moveOf(familyId));
            }
            return true;
        });
        if (Boolean.TRUE.equals(settledHere)) {
            log.info("리그 방을 정산했다: {} {} {}번 방", round.month(), round.tier(), round.groupNo());
        }
        return requireRound(roundId);
    }

    private LeagueRound requireRound(UUID roundId) {
        return Objects.requireNonNull(repository.findRound(roundId), () -> "리그 방이 없습니다: " + roundId);
    }
}
