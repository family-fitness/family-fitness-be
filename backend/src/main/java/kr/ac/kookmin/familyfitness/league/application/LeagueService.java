package kr.ac.kookmin.familyfitness.league.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.InvalidLeagueMonthException;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueNotFoundException;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTable;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GET /families/{familyId}/league — 같은 가족이면 누구나 본다. 「오늘」 은 앱 시간대(KST)다.
 *
 * <pre>
 * 이번 달  방이 없으면 여기서 넣고(브론즈에서 시작), 방 가족 모두의 달성률을 지금 센다 — 칸을 끝내면 FE 가 곧바로 다시 부른다
 * 지난달   정산 기록(final_rate)으로 답한다. 정산 전이면 먼저 정산한다. 그달 방에 없던 가족이면 404 LEAGUE_NOT_FOUND
 * 앞 달    422 INVALID_DATE
 * </pre>
 *
 * 방 배정 · 정산은 저마다 트랜잭션을 커밋한 뒤 끝난다. 그다음 읽기(방 · 달성률 · 가족 이름)는 읽기 전용 트랜잭션 하나에서 한다 —
 * 그 안에서 부르는 다른 모듈 API 가 그 트랜잭션에 합류해 호출마다 트랜잭션 · 커넥션을 새로 잡지 않는다.
 */
@Service
public class LeagueService {
    /** 가족 이름을 못 읽었을 때(가족이 지워진 경우) 쓰는 이름 */
    static final String UNKNOWN_FAMILY_NAME = "다른 가족";

    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final LeagueRepository repository;
    private final LeagueEnrollment enrollment;
    private final LeagueSettlement settlement;
    private final FamilyRates rates;
    private final TransactionTemplate readOnly;
    private final Clock clock;
    private final ZoneId zone;

    public LeagueService(
            FamilyAccess familyAccess,
            ProfileQuery profiles,
            LeagueRepository repository,
            LeagueEnrollment enrollment,
            LeagueSettlement settlement,
            FamilyRates rates,
            TransactionTemplate tx,
            Clock clock,
            ZoneId appZone) {
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.repository = repository;
        this.enrollment = enrollment;
        this.settlement = settlement;
        this.rates = rates;
        this.readOnly = new TransactionTemplate(Objects.requireNonNull(tx.getTransactionManager()));
        this.readOnly.setReadOnly(true);
        this.clock = clock;
        this.zone = appZone;
    }

    /** {@code month} 가 없으면 이번 달. */
    public LeagueView league(UUID userId, UUID familyId, @Nullable YearMonth month) {
        familyAccess.requireMember(userId, familyId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        YearMonth current = YearMonth.from(today);
        YearMonth target = month != null ? month : current;
        if (target.isAfter(current)) throw new InvalidLeagueMonthException("아직 오지 않은 달입니다: " + target);
        return target.equals(current) ? live(familyId, current, today) : settled(familyId, target);
    }

    private LeagueView live(UUID familyId, YearMonth month, LocalDate today) {
        LeagueMember me = enrollment.ensureMember(familyId, month);
        return read(() -> {
            LeagueRound round = requireRound(me.roundId());
            List<LeagueMember> members = repository.membersOf(round.id());
            Map<UUID, @Nullable Integer> liveRates =
                    rates.of(members.stream().map(LeagueMember::familyId).toList(), month, today);
            LeagueTable table = table(round, members, it -> liveRates.get(it.familyId()));
            return view(month, round, table, familyId, month.lengthOfMonth() - today.getDayOfMonth());
        });
    }

    private LeagueView settled(UUID familyId, YearMonth month) {
        LeagueMember me = repository.findMember(familyId, month);
        if (me == null) throw new LeagueNotFoundException();
        LeagueRound round = settlement.settle(me.roundId());
        return read(() -> {
            LeagueTable table = table(round, repository.membersOf(round.id()), LeagueMember::finalRate);
            return view(month, round, table, familyId, 0);
        });
    }

    private LeagueView read(Supplier<LeagueView> work) {
        return Objects.requireNonNull(readOnly.execute(status -> work.get()));
    }

    private static LeagueTable table(
            LeagueRound round, List<LeagueMember> members, Function<LeagueMember, @Nullable Integer> rateOf) {
        return new LeagueTable(
                round.tier(),
                members.stream()
                        .map(it -> new LeagueTable.Seat(it.familyId(), it.seatNo(), rateOf.apply(it)))
                        .toList());
    }

    private LeagueView view(YearMonth month, LeagueRound round, LeagueTable table, UUID me, int daysLeft) {
        List<LeagueTable.Seat> ordered = table.orderedFor(me);
        Map<UUID, String> names = profiles.familyNames(
                ordered.stream().map(LeagueTable.Seat::familyId).toList());
        List<LeagueView.Standing> standings = ordered.stream()
                .map(it -> new LeagueView.Standing(
                        names.getOrDefault(it.familyId(), UNKNOWN_FAMILY_NAME),
                        it.rate(),
                        it.familyId().equals(me)))
                .toList();
        Integer rate = ordered.stream()
                .filter(it -> it.familyId().equals(me))
                .findFirst()
                .map(LeagueTable.Seat::rate)
                .orElse(null);
        return new LeagueView(
                month.toString(),
                round.tier(),
                rate,
                table.rankOf(me),
                table.groupSize(),
                table.promote(),
                table.demote(),
                daysLeft,
                standings);
    }

    private LeagueRound requireRound(UUID roundId) {
        return Objects.requireNonNull(repository.findRound(roundId), () -> "리그 방이 없습니다: " + roundId);
    }
}
