package kr.ac.kookmin.familyfitness.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.coaching.support.NoopTransactionManager;
import kr.ac.kookmin.familyfitness.identity.api.AccountQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMember;
import kr.ac.kookmin.familyfitness.league.domain.LeagueMove;
import kr.ac.kookmin.familyfitness.league.domain.LeagueRound;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDaysSinceCreated;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가족 리그 조회 · 방 배정 · 월초 정산. 기준 시각은 2026-09-15(화) 10:00 KST 이고,
 * 달이 바뀌는 때는 2026-10-01 00:05 KST(정산 전)와 00:10 KST(정산)로 본다.
 */
class LeagueServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-09-15 10:00 KST */
    private static final Instant MID_SEPTEMBER = Instant.parse("2026-09-15T01:00:00Z");
    /** 2026-10-01 00:05 KST — 정산 스케줄러(00:10) 전 */
    private static final Instant OCTOBER_BEFORE_SETTLE = Instant.parse("2026-09-30T15:05:00Z");
    /** 2026-10-01 00:10 KST */
    private static final Instant OCTOBER_SETTLE = Instant.parse("2026-09-30T15:10:00Z");

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    /** 9월 1~10일 가운데 8일을 해낸 가족의 9/15 점수 — 0.8 × ln 9 ÷ ln 15(지난 날 9/1~9/14) */
    private static final double EIGHT_OF_FOURTEEN = 0.6491;

    private final InMemoryLeagueRepository repository = new InMemoryLeagueRepository();
    private final FakeActivity activity = spy(new FakeActivity());
    private final ProfileQuery profiles = mock(ProfileQuery.class);
    private final RestDayQuery restDayQuery = mock(RestDayQuery.class);
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    /** 심사용 계정 — 이 계정으로 부르면 체험 방을 받는다 */
    private final UUID reviewer = UUID.randomUUID();

    private final AccountQuery accounts = reviewer::equals;
    /** 프로필 → 잡힌 날(만든 날 이후) */
    private final Map<UUID, Set<LocalDate>> planned = new HashMap<>();
    /** 가족 → 쉬는 날 */
    private final Map<UUID, Set<LocalDate>> restDays = new HashMap<>();
    /** 가족 → 식구 */
    private final Map<UUID, List<ProfileSummary>> families = new LinkedHashMap<>();

    private final Map<UUID, String> names = new HashMap<>();
    private final UUID user = UUID.randomUUID();

    private final PlannedDaysSinceCreated plannedDays = (ids, from, to) -> {
        Map<UUID, Set<LocalDate>> out = new HashMap<>();
        for (UUID id : ids) {
            Set<LocalDate> days = new HashSet<>();
            planned.getOrDefault(id, Set.of()).stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .forEach(days::add);
            if (!days.isEmpty()) out.put(id, days);
        }
        return out;
    };

    /** 방 전체를 한 번에 읽는 쪽만 흉내 낸다 — 가족마다 따로 읽는 메서드를 부르면 빈 값이 나와 시험이 깨진다. */
    @BeforeEach
    void setUp() {
        when(profiles.summariesOfFamilies(any())).thenAnswer(call -> {
            Map<UUID, List<ProfileSummary>> out = new HashMap<>();
            call.<Collection<UUID>>getArgument(0).forEach(it -> out.put(it, families.getOrDefault(it, List.of())));
            return out;
        });
        when(profiles.familyNames(any())).thenAnswer(call -> {
            Map<UUID, String> out = new HashMap<>();
            call.<Collection<UUID>>getArgument(0).stream()
                    .filter(names::containsKey)
                    .forEach(it -> out.put(it, names.get(it)));
            return out;
        });
        when(restDayQuery.restDaysOfFamilies(any(), any(), any())).thenAnswer(call -> {
            LocalDate from = call.getArgument(1);
            LocalDate to = call.getArgument(2);
            Map<UUID, List<LocalDate>> out = new HashMap<>();
            call.<Collection<UUID>>getArgument(0)
                    .forEach(it -> out.put(
                            it,
                            restDays.getOrDefault(it, Set.of()).stream()
                                    .filter(day -> !day.isBefore(from) && !day.isAfter(to))
                                    .sorted()
                                    .toList()));
            return out;
        });
    }

    /** 그 시각의 서비스 묶음 — 조회 · 배정 · 정산 · 스케줄러가 같은 저장소를 쓴다. */
    private record At(LeagueService service, LeagueSettlementScheduler scheduler) {}

    private At at(Instant now) {
        Clock clock = Clock.fixed(now, KST);
        TransactionTemplate tx = NoopTransactionManager.noopTransactionTemplate();
        FamilyRates rates = new FamilyRates(profiles, plannedDays, activity, restDayQuery);
        LeagueSettlement settlement = new LeagueSettlement(repository, rates, tx, clock, KST);
        LeagueEnrollment enrollment = new LeagueEnrollment(repository, settlement, tx, clock);
        return new At(
                new LeagueService(
                        familyAccess, profiles, accounts, repository, enrollment, settlement, rates, tx, clock, KST),
                new LeagueSettlementScheduler(repository, settlement, enrollment, clock, KST));
    }

    private ProfileSummary profile(UUID familyId, ProfileRole role, boolean consentRequired, boolean consentGiven) {
        return new ProfileSummary(
                UUID.randomUUID(),
                familyId,
                role == ProfileRole.PARENT ? "엄마" : "아이",
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                consentRequired,
                consentGiven);
    }

    /** 보호자 한 명과 아이 {@code kids} 명인 가족. */
    private UUID family(String name, int kids) {
        UUID familyId = UUID.randomUUID();
        List<ProfileSummary> members = new ArrayList<>();
        members.add(profile(familyId, ProfileRole.PARENT, false, true));
        for (int i = 0; i < kids; i++) members.add(profile(familyId, ProfileRole.CHILD, true, true));
        families.put(familyId, members);
        names.put(familyId, name);
        return familyId;
    }

    private List<UUID> kidsOf(UUID familyId) {
        return families.get(familyId).stream()
                .filter(it -> it.role() == ProfileRole.CHILD)
                .map(ProfileSummary::profileId)
                .toList();
    }

    private UUID kidOf(UUID familyId) {
        return kidsOf(familyId).getFirst();
    }

    private void plan(UUID profileId, LocalDate... days) {
        planned.computeIfAbsent(profileId, it -> new HashSet<>()).addAll(List.of(days));
    }

    private void move(UUID profileId, LocalDate... days) {
        for (LocalDate day : days) activity.addActiveMinutes(profileId, day, ActivitySource.TIMER, 5);
    }

    private static LocalDate sep(int day) {
        return SEPTEMBER.atDay(day);
    }

    /** 9월 1~10일이 잡혔고 그중 앞 {@code done} 일을 해낸 아이 한 명 — 9월 달성률 done × 10 %. */
    private UUID familyWithSeptemberRate(String name, int done) {
        UUID familyId = family(name, 1);
        UUID kid = kidOf(familyId);
        for (int day = 1; day <= 10; day++) plan(kid, sep(day));
        for (int day = 1; day <= done; day++) move(kid, sep(day));
        return familyId;
    }

    /** 9월 골드 방 하나에 가족들을 들어온 차례대로 앉힌다(방은 정산 전). */
    private LeagueRound goldSeptember(List<UUID> familyIds) {
        LeagueRound round = new LeagueRound(
                UUID.randomUUID(), SEPTEMBER, LeagueTier.GOLD, 1, Instant.parse("2026-09-01T00:00:00Z"), null);
        repository.rounds.add(round);
        for (int i = 0; i < familyIds.size(); i++) {
            repository.members.add(LeagueMember.join(
                    round,
                    familyIds.get(i),
                    i + 1,
                    Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i)));
        }
        return round;
    }

    @Test
    @DisplayName("처음 여는 가족 — 브론즈 방에 들어가고, 셀 날이 없으면 달성률 · 순위가 null 이다(0% · 꼴찌가 아니다)")
    void 처음_여는_가족() {
        UUID familyId = family("서준이네", 1);

        LeagueView view = at(MID_SEPTEMBER).service().league(user, familyId, null);

        assertThat(view.month()).isEqualTo("2026-09");
        assertThat(view.tier()).isEqualTo(LeagueTier.BRONZE);
        assertThat(view.rate()).isNull();
        assertThat(view.rank()).isNull();
        assertThat(view.groupSize()).isEqualTo(1);
        assertThat(view.promote()).isZero();
        assertThat(view.demote()).isZero();
        assertThat(view.daysLeft()).isEqualTo(15);
        assertThat(view.standings()).containsExactly(new LeagueView.Standing("서준이네", null, null, true));
        assertThat(repository.rounds).hasSize(1);
        assertThat(repository.members).hasSize(1);

        at(MID_SEPTEMBER).service().league(user, familyId, SEPTEMBER);
        assertThat(repository.members).as("두 번 열어도 자리는 하나다").hasSize(1);
    }

    @Test
    @DisplayName("달성률 — 아이마다 잡힌 날 중 해낸 날(쉬는 날 빼고, 오늘은 해냈을 때만), 부모 · 걸음수는 겨루지 않는다")
    void 달성률() {
        UUID familyId = family("서준이네", 2);
        UUID kid = kidOf(familyId);
        UUID parent = families.get(familyId).getFirst().profileId();
        plan(kid, sep(1), sep(2), sep(3), sep(4), sep(15));
        move(kid, sep(1), sep(3), sep(10));
        move(parent, sep(1), sep(2), sep(3), sep(4), sep(15));
        activity.overwriteSteps(kid, sep(4), 9000);
        restDays.put(familyId, Set.of(sep(2)));

        // 센 날 = 1 · 3 · 4(2일은 쉬는 날, 오늘 15일은 아직), 해낸 날 = 1 · 3 → 67. 둘째는 잡힌 날이 없어 평균에서 빠진다
        assertThat(at(MID_SEPTEMBER).service().league(user, familyId, null).rate())
                .isEqualTo(67);

        move(kid, sep(15));
        LeagueView view = at(MID_SEPTEMBER).service().league(user, familyId, null);
        assertThat(view.rate()).isEqualTo(75);
        assertThat(view.rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("보호자 동의가 필요한데 없는(거둔) 아이는 셈에서 뺀다 — 기록할 수 없는 아이가 가족 값을 끌어내리지 않는다")
    void 동의_거둔_아이() {
        UUID familyId = UUID.randomUUID();
        ProfileSummary kid = profile(familyId, ProfileRole.CHILD, true, true);
        ProfileSummary withdrawn = profile(familyId, ProfileRole.CHILD, true, false);
        families.put(familyId, List.of(profile(familyId, ProfileRole.PARENT, false, true), kid, withdrawn));
        names.put(familyId, "서준이네");
        plan(kid.profileId(), sep(1), sep(2));
        move(kid.profileId(), sep(1), sep(2));
        plan(withdrawn.profileId(), sep(1), sep(2));

        assertThat(at(MID_SEPTEMBER).service().league(user, familyId, null).rate())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("순위표에는 가족 이름 · 달성률만 — 같으면 보는 가족이 먼저, 두 집 모두 자기를 1등으로 본다")
    void 순위표와_동률() {
        UUID first = familyWithSeptemberRate("서준이네", 8);
        UUID second = familyWithSeptemberRate("하윤이네", 8);
        UUID empty = family("지호네", 1);
        at(MID_SEPTEMBER).service().league(user, first, null);
        at(MID_SEPTEMBER).service().league(user, empty, null);
        LeagueView secondView = at(MID_SEPTEMBER).service().league(user, second, null);
        LeagueView firstView = at(MID_SEPTEMBER).service().league(user, first, null);

        assertThat(secondView.standings())
                .containsExactly(
                        new LeagueView.Standing("하윤이네", 80, EIGHT_OF_FOURTEEN, true),
                        new LeagueView.Standing("서준이네", 80, EIGHT_OF_FOURTEEN, false),
                        new LeagueView.Standing("지호네", null, null, false));
        assertThat(secondView.rank()).isEqualTo(1);
        assertThat(firstView.standings().getFirst())
                .isEqualTo(new LeagueView.Standing("서준이네", 80, EIGHT_OF_FOURTEEN, true));
        assertThat(firstView.rank()).isEqualTo(1);
        assertThat(firstView.groupSize()).isEqualTo(3);
        assertThat(firstView.promote()).as("8가족 미만 방").isZero();
    }

    @Test
    @DisplayName("한 방은 열 가족까지 — 열한 번째 가족은 같은 티어의 새 방에 들어간다")
    void 방_정원() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) ids.add(family("가족" + i, 1));
        ids.forEach(it -> at(MID_SEPTEMBER).service().league(user, it, null));

        assertThat(repository.rounds)
                .extracting(LeagueRound::tier, LeagueRound::groupNo)
                .containsExactly(tuple(LeagueTier.BRONZE, 1), tuple(LeagueTier.BRONZE, 2));
        assertThat(at(MID_SEPTEMBER).service().league(user, ids.get(9), null).groupSize())
                .isEqualTo(10);
        assertThat(at(MID_SEPTEMBER).service().league(user, ids.get(10), null).groupSize())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("앞 달은 422 INVALID_DATE, 그달 방에 없던 지난달은 404 LEAGUE_NOT_FOUND, 다른 가족은 403")
    void 잘못된_요청() {
        UUID familyId = family("서준이네", 1);
        LeagueService service = at(MID_SEPTEMBER).service();

        assertThatThrownBy(() -> service.league(user, familyId, OCTOBER))
                .isInstanceOf(DomainException.class)
                .extracting(it -> ((DomainException) it).getCode())
                .isEqualTo("INVALID_DATE");
        assertThatThrownBy(() -> service.league(user, familyId, YearMonth.of(2026, 8)))
                .isInstanceOf(DomainException.class)
                .extracting(it -> ((DomainException) it).getCode())
                .isEqualTo("LEAGUE_NOT_FOUND");

        UUID stranger = UUID.randomUUID();
        when(familyAccess.requireMember(stranger, familyId)).thenThrow(new NotSameFamilyException());
        assertThatThrownBy(() -> service.league(stranger, familyId, null)).isInstanceOf(NotSameFamilyException.class);
        assertThat(repository.members).isEmpty();
    }

    @Test
    @DisplayName("1일 00:10 정산 — 위 셋은 올라가고 아래 셋은 내려가고, 달성률 없는 집은 머문다. 두 번 돌아도 같다")
    void 월초_정산() {
        List<UUID> ids = new ArrayList<>();
        for (int done = 9; done >= 2; done--) ids.add(familyWithSeptemberRate("가족" + done, done));
        ids.add(family("빈집1", 1));
        ids.add(family("빈집2", 1));
        goldSeptember(ids);

        LeagueSettlementScheduler.MonthClose first =
                at(OCTOBER_SETTLE).scheduler().run();

        assertThat(first).isEqualTo(new LeagueSettlementScheduler.MonthClose(1, 10, 0));
        assertThat(repository.members.stream()
                        .filter(it -> it.month().equals(SEPTEMBER))
                        .map(LeagueMember::moved))
                .containsExactly(
                        LeagueMove.UP,
                        LeagueMove.UP,
                        LeagueMove.UP,
                        LeagueMove.STAY,
                        LeagueMove.STAY,
                        LeagueMove.DOWN,
                        LeagueMove.DOWN,
                        LeagueMove.DOWN,
                        LeagueMove.STAY,
                        LeagueMove.STAY);
        assertThat(repository.members.stream()
                        .filter(it -> it.month().equals(SEPTEMBER))
                        .map(LeagueMember::finalRate))
                .containsExactly(90, 80, 70, 60, 50, 40, 30, 20, null, null);
        LeagueService october = at(OCTOBER_SETTLE).service();
        assertThat(ids.stream().map(it -> october.league(user, it, null).tier()))
                .containsExactly(
                        LeagueTier.PLATINUM,
                        LeagueTier.PLATINUM,
                        LeagueTier.PLATINUM,
                        LeagueTier.GOLD,
                        LeagueTier.GOLD,
                        LeagueTier.SILVER,
                        LeagueTier.SILVER,
                        LeagueTier.SILVER,
                        LeagueTier.GOLD,
                        LeagueTier.GOLD);

        int settleCalls = repository.markSettledCalls;
        LeagueSettlementScheduler.MonthClose again =
                at(OCTOBER_SETTLE).scheduler().run();
        assertThat(again).isEqualTo(new LeagueSettlementScheduler.MonthClose(0, 0, 0));
        assertThat(repository.markSettledCalls).isEqualTo(settleCalls);
        assertThat(repository.members.stream().filter(it -> it.month().equals(OCTOBER)))
                .hasSize(10);
    }

    @Test
    @DisplayName("1일 00:10 전에 연 가족 — 지난달 방을 먼저 정산하고 새 티어로 들어간다. 뒤이은 스케줄러는 나머지만 넣는다")
    void 정산_전에_연_가족() {
        List<UUID> ids = new ArrayList<>();
        for (int done = 10; done >= 3; done--) ids.add(familyWithSeptemberRate("가족" + done, done));
        goldSeptember(ids);

        LeagueView early = at(OCTOBER_BEFORE_SETTLE).service().league(user, ids.getFirst(), null);

        assertThat(early.tier()).isEqualTo(LeagueTier.PLATINUM);
        assertThat(early.month()).isEqualTo("2026-10");
        assertThat(early.rate()).as("10월 1일 아침 — 셀 날이 없다").isNull();
        assertThat(early.daysLeft()).isEqualTo(30);
        assertThat(repository.rounds.stream().filter(it -> it.month().equals(SEPTEMBER)))
                .allMatch(LeagueRound::isSettled);

        LeagueSettlementScheduler.MonthClose close =
                at(OCTOBER_SETTLE).scheduler().run();
        assertThat(close).isEqualTo(new LeagueSettlementScheduler.MonthClose(0, 7, 0));
        assertThat(repository.members.stream().filter(it -> it.month().equals(OCTOBER)))
                .hasSize(8);
    }

    @Test
    @DisplayName("지난달은 정산 기록으로 답한다 — 정산 뒤에 들어온 기록은 지난달 값을 바꾸지 않는다")
    void 지난달은_정산_기록() {
        List<UUID> ids = new ArrayList<>();
        for (int done = 9; done >= 2; done--) ids.add(familyWithSeptemberRate("가족" + done, done));
        goldSeptember(ids);
        at(OCTOBER_SETTLE).scheduler().run();
        move(kidOf(ids.getLast()), sep(3), sep(4), sep(5), sep(6), sep(7), sep(8), sep(9), sep(10));

        LeagueView september = at(OCTOBER_SETTLE).service().league(user, ids.getLast(), SEPTEMBER);

        assertThat(september.month()).isEqualTo("2026-09");
        assertThat(september.tier()).isEqualTo(LeagueTier.GOLD);
        assertThat(september.rate()).isEqualTo(20);
        assertThat(september.rank()).isEqualTo(8);
        assertThat(september.groupSize()).isEqualTo(8);
        assertThat(september.promote()).isEqualTo(3);
        assertThat(september.demote()).isEqualTo(3);
        assertThat(september.daysLeft()).isZero();
        assertThat(september.standings())
                .extracting(LeagueView.Standing::rate)
                .containsExactly(90, 80, 70, 60, 50, 40, 30, 20);
        assertThat(september.standings().getLast().me()).isTrue();
    }

    @Test
    @DisplayName("열 가족 방 조회 한 번 — 식구 · 가족 이름 · 움직인 날 · 쉬는 날을 방 전체로 한 번씩만 읽는다(가족마다 따로 읽지 않는다)")
    void 방_전체를_한_번에_읽는다() {
        List<UUID> ids = new ArrayList<>();
        for (int done = 1; done <= 10; done++) ids.add(familyWithSeptemberRate("가족" + done, done));
        ids.forEach(it -> at(MID_SEPTEMBER).service().league(user, it, null));
        clearInvocations(profiles, activity, restDayQuery);

        LeagueView view = at(MID_SEPTEMBER).service().league(user, ids.getFirst(), null);

        assertThat(view.groupSize()).isEqualTo(10);
        assertThat(view.standings().getFirst()).isEqualTo(new LeagueView.Standing("가족10", 100, 0.8855, false));
        assertThat(view.standings().getLast()).isEqualTo(new LeagueView.Standing("가족1", 10, 0.0256, true));
        verify(profiles, times(1)).summariesOfFamilies(any());
        verify(profiles, times(1)).familyNames(any());
        verify(profiles, never()).summariesOfFamily(any());
        verify(profiles, never()).familyName(any());
        verify(activity, times(1)).verifiedDaysOf(any(), any(), any());
        verify(restDayQuery, times(1)).restDaysOfFamilies(any(), any(), any());
        verify(restDayQuery, never()).restDaysBetween(any(), any(), any());
    }

    @Test
    @DisplayName("다섯 번 연달아 자리를 뺏기면 더 시도하지 않고 409 LEAGUE_BUSY — 우리 가족 자리는 남지 않는다")
    void 자리를_계속_뺏기면_LEAGUE_BUSY() {
        UUID familyId = family("서준이네", 1);
        LeagueRound room = new LeagueRound(
                UUID.randomUUID(), SEPTEMBER, LeagueTier.BRONZE, 1, Instant.parse("2026-09-01T00:00:00Z"), null);
        repository.rounds.add(room);
        // 매번 우리가 고른 자리에 다른 가족이 먼저 앉는다
        for (int i = 0; i < LeagueEnrollment.MAX_ATTEMPTS; i++) {
            repository.beforeNextMemberInsert(chosen -> repository.members.add(
                    LeagueMember.join(room, UUID.randomUUID(), chosen.seatNo(), chosen.joinedAt())));
        }
        LeagueService service = at(MID_SEPTEMBER).service();

        assertThatThrownBy(() -> service.league(user, familyId, null))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LEAGUE_BUSY");
                    assertThat(e.getKind()).isEqualTo(ErrorKind.CONFLICT);
                });
        assertThat(repository.memberInserts).isEqualTo(LeagueEnrollment.MAX_ATTEMPTS);
        assertThat(repository.findMember(familyId, SEPTEMBER)).isNull();
        assertThat(repository.membersOf(room.id()))
                .extracting(LeagueMember::seatNo)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("리그를 한 번도 안 연 가족은 월초에 넣지 않는다 — 지난달 방에 있던 가족만 새 달 방으로 옮긴다")
    void 안_연_가족() {
        UUID opened = family("서준이네", 1);
        family("하윤이네", 1);
        at(MID_SEPTEMBER).service().league(user, opened, null);

        at(OCTOBER_SETTLE).scheduler().run();

        assertThat(repository.members.stream()
                        .filter(it -> it.month().equals(OCTOBER))
                        .map(LeagueMember::familyId))
                .containsExactly(opened);
    }

    @Test
    @DisplayName("순위 점수 — 편성을 거절하고 쉬다 하루 해낸 100% 가족은 날마다 해낸 가족 · 거의 날마다 해낸 가족 뒤다")
    void 순위_점수() {
        UUID oneDay = family("하루네", 1);
        plan(kidOf(oneDay), sep(10));
        move(kidOf(oneDay), sep(10));
        UUID daily = family("매일네", 1);
        UUID most = family("거의네", 1);
        for (int day = 1; day <= 14; day++) {
            plan(kidOf(daily), sep(day));
            move(kidOf(daily), sep(day));
            plan(kidOf(most), sep(day));
            if (day <= 12) move(kidOf(most), sep(day));
        }
        goldSeptember(List.of(oneDay, most, daily));

        // 지난 날 = 9/1~9/14(오늘 15일은 아직 안 움직여 빠진다) → 14일
        LeagueView view = at(MID_SEPTEMBER).service().league(user, oneDay, null);

        assertThat(view.rate()).as("달성률은 그대로 둔다").isEqualTo(100);
        assertThat(view.score()).isCloseTo(Math.log(2) / Math.log(15), within(0.001));
        assertThat(view.rank()).isEqualTo(3);
        assertThat(view.standings())
                .extracting(LeagueView.Standing::familyName, LeagueView.Standing::rate)
                .containsExactly(tuple("매일네", 100), tuple("거의네", 86), tuple("하루네", 100));
        assertThat(view.standings().getFirst().score()).as("날마다 다 하면 1").isEqualTo(1.0);
        assertThat(view.standings().get(1).score()).isCloseTo(12.0 / 14 * Math.log(13) / Math.log(15), within(0.001));

        at(OCTOBER_SETTLE).scheduler().run();
        LeagueMember settled = repository.findMember(oneDay, SEPTEMBER);
        assertThat(settled.finalRate()).isEqualTo(100);
        assertThat(settled.finalRank()).as("정산 순위도 점수로 매긴다").isEqualTo(3);
        assertThat(settled.finalScore()).isNotNull();
        assertThat(repository.findMember(daily, SEPTEMBER).finalRank()).isEqualTo(1);
        assertThat(at(OCTOBER_SETTLE).service().league(user, oneDay, SEPTEMBER).standings())
                .extracting(LeagueView.Standing::familyName)
                .containsExactly("매일네", "거의네", "하루네");
    }

    @Test
    @DisplayName("체험 가족(심사용 계정)이 리그를 열면 실제 방에 넣지 않고 그 가족 + 가짜 가족 일곱의 체험 방을 돌려준다 — 가짜 가족은 가족 id 로 늘 같다")
    void 체험_가족은_체험_방을_받는다() {
        UUID trial = family("체험 가족", 1);
        plan(kidOf(trial), sep(14));
        move(kidOf(trial), sep(14));

        LeagueView view = at(MID_SEPTEMBER).service().league(reviewer, trial, null);

        assertThat(view.groupSize()).isEqualTo(8);
        assertThat(view.standings()).hasSize(8);
        assertThat(view.tier()).isEqualTo(LeagueTier.START);
        assertThat(view.month()).isEqualTo("2026-09");
        assertThat(view.daysLeft()).isEqualTo(15);
        assertThat(view.rate()).isEqualTo(100);
        assertThat(view.standings())
                .filteredOn(LeagueView.Standing::me)
                .singleElement()
                .extracting(LeagueView.Standing::familyName)
                .isEqualTo("체험 가족");
        assertThat(view.standings())
                .extracting(LeagueView.Standing::familyName)
                .doesNotHaveDuplicates()
                .doesNotContainNull();
        assertThat(view.standings()).allSatisfy(it -> {
            assertThat(it.rate()).isNotNull();
            assertThat(it.score()).isBetween(0.0, 1.0);
        });
        assertThat(view.standings())
                .extracting(LeagueView.Standing::score)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(at(MID_SEPTEMBER).service().league(reviewer, trial, null))
                .as("같은 날에는 부를 때마다 같은 가짜 가족을 본다")
                .isEqualTo(view);
        assertThat(repository.rounds).as("DB 의 실제 방을 만들지 않는다").isEmpty();
        assertThat(repository.members).isEmpty();

        UUID otherTrial = family("체험 가족", 1);
        assertThat(at(MID_SEPTEMBER)
                        .service()
                        .league(reviewer, otherTrial, null)
                        .standings())
                .extracting(LeagueView.Standing::familyName)
                .as("다른 체험 가족은 다른 가짜 가족을 본다")
                .isNotEqualTo(view.standings().stream()
                        .map(LeagueView.Standing::familyName)
                        .toList());
    }

    @Test
    @DisplayName("체험 방의 가짜 가족 점수는 그달에 지난 날로 센다 — 달 첫날에는 하루를 했거나 안 했거나라 달성률 0 · 점수 0 이거나 달성률 100 · 점수 1 이다")
    void 체험_방_가짜_가족은_달_첫날에_하루치_점수만_있다() {
        UUID trial = family("체험 가족", 1);

        LeagueView firstDay = at(OCTOBER_SETTLE).service().league(reviewer, trial, null);

        assertThat(firstDay.month()).isEqualTo("2026-10");
        assertThat(firstDay.standings())
                .filteredOn(it -> !it.me())
                .hasSize(TrialLeague.FAKE_FAMILIES)
                .allSatisfy(it -> assertThat(List.of(it.rate(), it.score())).isIn(List.of(0, 0.0), List.of(100, 1.0)));
    }

    @Test
    @DisplayName("체험 방의 가짜 가족 점수는 실제 가족과 같은 공식이다 — 운동한 날 = 지난 날 × 달성률, 점수 = 달성률 × ln(1+운동한 날) ÷ ln(1+지난 날)")
    void 체험_방_가짜_가족_점수는_실제와_같은_공식() {
        UUID trial = family("체험 가족", 1);

        LeagueView view = at(MID_SEPTEMBER).service().league(reviewer, trial, null);

        int elapsed = 15;
        assertThat(view.standings()).filteredOn(it -> !it.me()).allSatisfy(it -> {
            long moved = Math.round(elapsed * it.rate() / 100.0);
            double expected = (double) moved / elapsed * Math.log1p(moved) / Math.log1p(elapsed);
            assertThat(it.score()).isCloseTo(expected, org.assertj.core.api.Assertions.within(0.0001));
        });
    }

    @Test
    @DisplayName("실제 가족의 방에는 체험 가족이 없고, 월초 정산은 체험 가족을 건드리지 않는다")
    void 실제_방과_정산에_체험_가족이_없다() {
        UUID real = familyWithSeptemberRate("서준이네", 5);
        UUID trial = family("체험 가족", 1);
        at(MID_SEPTEMBER).service().league(reviewer, trial, null);
        LeagueView realView = at(MID_SEPTEMBER).service().league(user, real, null);

        assertThat(realView.groupSize()).isEqualTo(1);
        assertThat(realView.standings())
                .extracting(LeagueView.Standing::familyName)
                .containsExactly("서준이네");
        assertThat(repository.members).extracting(LeagueMember::familyId).containsExactly(real);

        at(OCTOBER_SETTLE).scheduler().run();

        assertThat(repository.members).extracting(LeagueMember::familyId).containsOnly(real);
        assertThat(repository.findMember(trial, SEPTEMBER)).isNull();
        assertThat(repository.findMember(trial, OCTOBER)).isNull();
        assertThat(at(OCTOBER_SETTLE).service().league(reviewer, trial, null).groupSize())
                .as("다음 달에도 체험 방")
                .isEqualTo(8);
        assertThat(repository.findMember(trial, OCTOBER)).isNull();
        assertThatThrownBy(() -> at(OCTOBER_SETTLE).service().league(reviewer, trial, SEPTEMBER))
                .as("지난달 체험 방은 없다")
                .isInstanceOf(kr.ac.kookmin.familyfitness.league.domain.LeagueNotFoundException.class);
    }
}
