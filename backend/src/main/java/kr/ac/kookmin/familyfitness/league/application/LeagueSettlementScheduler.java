package kr.ac.kookmin.familyfitness.league.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 월초 리그 정산. 매월 1일 00:10(app.timezone 기준)에 지난달 방을 모두 정산하고, 지난달 방에 있던 가족을 새 티어의 이번 달 방에 넣는다.
 *
 * <p>여러 번 돌아도 결과가 같다(멱등). 방 정산은 {@link LeagueSettlement} 가 방마다 한 번만 하고, 방 배정은 그달 이미 자리가 있으면
 * 건너뛴다. 그래서 기동 때도 한 번 돌려 1일에 서버가 꺼져 있던 달을 따라잡는다. 리그를 한 번도 안 연 가족은 여기서 넣지 않는다 —
 * 첫 조회 때 브론즈로 들어간다({@link LeagueEnrollment}). 방 하나 · 가족 하나가 실패해도 나머지는 계속한다.
 */
@Component
public class LeagueSettlementScheduler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final LeagueRepository repository;
    private final LeagueSettlement settlement;
    private final LeagueEnrollment enrollment;
    private final Clock clock;
    private final ZoneId zone;

    public LeagueSettlementScheduler(
            LeagueRepository repository,
            LeagueSettlement settlement,
            LeagueEnrollment enrollment,
            Clock clock,
            ZoneId appZone) {
        this.repository = repository;
        this.settlement = settlement;
        this.enrollment = enrollment;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 기동 직후 한 번. 여기서 난 오류로 서버가 뜨지 못하면 안 되므로 로그만 남긴다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("기동 때 리그 정산 따라잡기 실패", e);
        }
    }

    /** 지난달을 닫는다. */
    @Scheduled(cron = "${app.league.settle-cron:0 10 0 1 * *}", zone = "${app.timezone:Asia/Seoul}")
    public MonthClose run() {
        return closeMonth(
                YearMonth.from(LocalDate.ofInstant(clock.instant(), zone)).minusMonths(1));
    }

    /** {@code month} 의 방을 모두 정산하고, 그달 가족을 다음 달 방에 넣는다. */
    public MonthClose closeMonth(YearMonth month) {
        int settled = 0;
        int failed = 0;
        for (LeagueRound round : repository.unsettledRoundsOf(month)) {
            try {
                settlement.settle(round.id());
                settled++;
            } catch (RuntimeException e) {
                failed++;
                log.error("리그 방 정산 실패: {} {} {}번 방", month, round.tier(), round.groupNo(), e);
            }
        }
        YearMonth next = month.plusMonths(1);
        int placed = 0;
        for (LeagueMember member : repository.membersOfMonth(month)) {
            if (repository.findMember(member.familyId(), next) != null) continue;
            try {
                enrollment.ensureMember(member.familyId(), next);
                placed++;
            } catch (RuntimeException e) {
                failed++;
                log.error("다음 달 리그 방 배정 실패: 가족 {} · {}", member.familyId(), next, e);
            }
        }
        if (settled > 0 || placed > 0 || failed > 0) {
            log.info("{} 리그를 닫았다 — 방 {}개 정산 · 가족 {}곳 {} 방 배정 · 실패 {}건", month, settled, placed, next, failed);
        }
        return new MonthClose(settled, placed, failed);
    }

    /**
     * 한 번 닫은 결과.
     *
     * @param settledRounds 이번에 정산한 방 수(이미 끝난 방은 세지 않는다)
     * @param placedFamilies 이번에 다음 달 방에 넣은 가족 수(이미 자리가 있던 가족은 세지 않는다)
     * @param failures 실패한 방 · 가족 수
     */
    public record MonthClose(int settledRounds, int placedFamilies, int failures) {}
}
