package kr.ac.kookmin.familyfitness.league.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueRepository;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate.Result;
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
 * 이번 달  방이 없으면 여기서 넣고(브론즈에서 시작), 방 가족 모두의 달성률 · 순위 점수를 지금 센다 — 칸을 끝내면 FE 가 곧바로 다시 부른다
 * 지난달   정산 기록(final_rate)으로 답한다. 정산 전이면 먼저 정산한다. 그달 방에 없던 가족이면 404 LEAGUE_NOT_FOUND
 * 앞 달    422 INVALID_DATE
 * 체험     심사용 계정(identity AccountQuery#isReviewAccount)이 부르면 실제 방 대신 체험 방({@link TrialLeague}) — 그 가족 + 가짜
 *          가족 일곱. DB 의 방 · 정산에 들어가지 않는다. 지난달은 404 LEAGUE_NOT_FOUND
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
    private final AccountQuery accounts;
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
            AccountQuery accounts,
            LeagueRepository repository,
            LeagueEnrollment enrollment,
            LeagueSettlement settlement,
            FamilyRates rates,
            TransactionTemplate tx,
            Clock clock,
            ZoneId appZone) {
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.accounts = accounts;
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
        if (accounts.isReviewAccount(userId)) return trial(familyId, target, current, today);
        return target.equals(current) ? live(familyId, current, today) : settled(familyId, target);
    }

    private LeagueView live(UUID familyId, YearMonth month, LocalDate today) {
        LeagueMember me = enrollment.ensureMember(familyId, month);
        return read(() -> {
            LeagueRound round = requireRound(me.roundId());
            List<LeagueMember> members = repository.membersOf(round.id());
            Map<UUID, @Nullable Result> liveRates =
                    rates.of(members.stream().map(LeagueMember::familyId).toList(), month, today);
            LeagueTable table = table(round, members, it -> seat(it, liveRates.get(it.familyId())));
            return view(month, table, familyId, month.lengthOfMonth() - today.getDayOfMonth(), Map.of());
        });
    }

    private LeagueView settled(UUID familyId, YearMonth month) {
        LeagueMember me = repository.findMember(familyId, month);
        if (me == null) throw new LeagueNotFoundException();
        LeagueRound round = settlement.settle(me.roundId());
        return read(() -> {
            LeagueTable table = table(round, repository.membersOf(round.id()), LeagueService::settledSeat);
            return view(month, table, familyId, 0, Map.of());
        });
    }

    /** 지금 센 달성률 · 점수로 앉힌 자리. */
    static LeagueTable.Seat seat(LeagueMember member, @Nullable Result result) {
        return new LeagueTable.Seat(
                member.familyId(),
                member.seatNo(),
                result != null ? result.rate() : null,
                result != null ? result.score() : null);
    }

    /** 정산 때 굳힌 값으로 앉힌 자리. 점수 칸을 만들기 전(V167 전)에 정산한 달은 달성률 ÷ 100 을 점수로 쓴다 — 그때 순위도 달성률로 매겼다. */
    private static LeagueTable.Seat settledSeat(LeagueMember member) {
        Integer rate = member.finalRate();
        Double score = member.finalScore();
        if (score == null && rate != null) score = rate / 100.0;
        return new LeagueTable.Seat(member.familyId(), member.seatNo(), rate, score);
    }

    /**
     * 심사용 체험 가족의 방({@link TrialLeague}). 실제 방에 넣지도 · 정산하지도 않는다. 이번 달만 있고 지난달은 404 LEAGUE_NOT_FOUND
     * (그달 방에 없던 가족과 같다).
     */
    private LeagueView trial(UUID familyId, YearMonth month, YearMonth current, LocalDate today) {
        if (!month.equals(current)) throw new LeagueNotFoundException();
        return read(() -> {
            TrialLeague.Room room = TrialLeague.of(
                    familyId, rates.of(List.of(familyId), month, today).get(familyId));
            return view(month, room.table(), familyId, month.lengthOfMonth() - today.getDayOfMonth(), room.names());
        });
    }

    private <T> T read(Supplier<T> work) {
        return Objects.requireNonNull(readOnly.execute(status -> work.get()));
    }

    private static LeagueTable table(
            LeagueRound round, List<LeagueMember> members, Function<LeagueMember, LeagueTable.Seat> seatOf) {
        return new LeagueTable(round.tier(), members.stream().map(seatOf).toList());
    }

    /** {@code fakeNames} 는 체험 방의 가짜 가족 이름. 실제 방이면 비어 있다. */
    private LeagueView view(YearMonth month, LeagueTable table, UUID me, int daysLeft, Map<UUID, String> fakeNames) {
        List<LeagueTable.Seat> ordered = table.orderedFor(me);
        Map<UUID, String> names = new HashMap<>(fakeNames);
        names.putAll(profiles.familyNames(ordered.stream()
                .map(LeagueTable.Seat::familyId)
                .filter(it -> !fakeNames.containsKey(it))
                .toList()));
        List<LeagueView.Standing> standings = ordered.stream()
                .map(it -> new LeagueView.Standing(
                        names.getOrDefault(it.familyId(), UNKNOWN_FAMILY_NAME),
                        it.rate(),
                        it.score(),
                        it.familyId().equals(me)))
                .toList();
        LeagueTable.Seat mine = ordered.stream()
                .filter(it -> it.familyId().equals(me))
                .findFirst()
                .orElse(null);
        return new LeagueView(
                month.toString(),
                table.tier(),
                mine != null ? mine.rate() : null,
                mine != null ? mine.score() : null,
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
