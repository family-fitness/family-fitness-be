package kr.ac.kookmin.familyfitness.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository;
import kr.ac.kookmin.familyfitness.activity.domain.RestCard;
import kr.ac.kookmin.familyfitness.coaching.support.NoopTransactionManager;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 쉬는 날 카드 규칙(FE 목 fe:src/mocks/league.ts 와 같은 검사 차례 · 코드).
 * 기준 시각은 2026-09-09(수) 10:00 KST 이고, 달 경계는 따로 2026-10-01 00:30 KST(UTC 로는 9/30)로 본다.
 */
class RestCardServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-09T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    private final UUID familyId = UUID.randomUUID();
    private final UUID parentUser = UUID.randomUUID();
    private final UUID childUser = UUID.randomUUID();
    private final ProfileSummary mom = summary("엄마", ProfileRole.PARENT);
    private final ProfileSummary kid = summary("서준", ProfileRole.CHILD);
    private final ProfileSummary sister = summary("서아", ProfileRole.CHILD);

    private final InMemoryRestCardRepository cards = new InMemoryRestCardRepository();
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final ProfileQuery profiles = mock(ProfileQuery.class);
    private final ActivityDailyRepository activities = mock(ActivityDailyRepository.class);
    /** 그날 운동한 프로필 */
    private final Map<LocalDate, Set<UUID>> moved = new HashMap<>();

    private RestCardService service = serviceAt(NOW);

    @BeforeEach
    void setUp() {
        when(familyAccess.requireMember(any(), any())).thenReturn(kid);
        when(familyAccess.requireParent(parentUser, familyId)).thenReturn(mom);
        when(familyAccess.requireParent(childUser, familyId)).thenThrow(new NotAParentException());
        when(profiles.summariesOfFamily(familyId)).thenReturn(List.of(mom, kid, sister));
        when(activities.anyActiveOn(anyCollection(), any(LocalDate.class))).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            LocalDate date = call.getArgument(1);
            return ids.stream().anyMatch(moved.getOrDefault(date, Set.of())::contains);
        });
    }

    private RestCardService serviceAt(Instant now) {
        return new RestCardService(
                cards,
                activities,
                familyAccess,
                profiles,
                NoopTransactionManager.noopTransactionTemplate(),
                Clock.fixed(now, KST),
                KST);
    }

    private ProfileSummary summary(String name, ProfileRole role) {
        return new ProfileSummary(
                UUID.randomUUID(),
                familyId,
                name,
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                role == ProfileRole.CHILD,
                true);
    }

    private RestCardsView use(String date) {
        return service.use(parentUser, familyId, date);
    }

    /** 다른 보호자가 이 요청과 거의 같은 순간에 이미 넣은 카드 */
    private void storeCard(LocalDate date, int cardNo) {
        cards.rows.add(RestCard.use(familyId, date, cardNo, mom.profileId(), NOW));
    }

    /** 다른 보호자가 그 카드를 되돌렸다 */
    private void removeCard(LocalDate date) {
        cards.rows.removeIf(card -> card.restDate().equals(date));
    }

    private void moved(ProfileSummary who, LocalDate date) {
        moved.computeIfAbsent(date, it -> new HashSet<>()).add(who.profileId());
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("달을 안 주면 KST 이번 달 — UTC 로 9/30 이어도 KST 로 10/1 이면 10월")
    void 달을_안_주면_KST_이번_달() {
        service = serviceAt(Instant.parse("2026-09-30T15:30:00Z"));

        RestCardsView view = service.month(childUser, familyId, null);

        assertThat(view.month()).isEqualTo("2026-10");
        assertThat(view.perMonth()).isEqualTo(2);
        assertThat(view.left()).isEqualTo(2);
        assertThat(view.days()).isEmpty();
    }

    @Test
    @DisplayName("보기는 그달 쉬는 날을 오름차순으로 주고 남은 장은 두 장에서 쓴 장을 뺀다 — 다른 달 카드는 섞지 않는다")
    void 보기는_그달_쉬는_날을_오름차순으로_준다() {
        storeCard(LocalDate.of(2026, 9, 20), 1);
        storeCard(LocalDate.of(2026, 9, 12), 2);
        storeCard(LocalDate.of(2026, 8, 30), 1);

        RestCardsView view = service.month(childUser, familyId, YearMonth.of(2026, 9));

        assertThat(view.month()).isEqualTo("2026-09");
        assertThat(view.left()).isZero();
        assertThat(view.days()).containsExactly(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("보호자가 오늘을 쉬는 날로 쓰면 1번 카드가 나가고 그달 모양으로 답한다")
    void 보호자가_오늘을_쉬는_날로_쓴다() {
        RestCardsView view = use("2026-09-09");

        assertThat(view.month()).isEqualTo("2026-09");
        assertThat(view.left()).isEqualTo(1);
        assertThat(view.days()).containsExactly(TODAY);
        assertThat(cards.rows).singleElement().satisfies(card -> {
            assertThat(card.cardNo()).isEqualTo(1);
            assertThat(card.createdBy()).isEqualTo(mom.profileId());
            assertThat(card.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("지난 날 · 다른 달 · 형식이 틀린 날 · 빈 날은 422 INVALID_DATE")
    void 쓸_수_없는_날은_INVALID_DATE() {
        assertCode(() -> use("2026-09-08"), "INVALID_DATE");
        assertCode(() -> use("2026-10-01"), "INVALID_DATE");
        assertCode(() -> use("2026-08-31"), "INVALID_DATE");
        assertCode(() -> use("2026/09/10"), "INVALID_DATE");
        assertCode(() -> use("2026-9-10"), "INVALID_DATE");
        assertCode(() -> use(" "), "INVALID_DATE");
        assertCode(() -> service.use(parentUser, familyId, null), "INVALID_DATE");
        assertThat(cards.insertCalls).isZero();
    }

    @Test
    @DisplayName("오늘 기준은 KST — UTC 로 9/30 15:30 이면 9/30 은 지난 날이고 10/1 은 쓸 수 있다")
    void 오늘_기준은_KST() {
        service = serviceAt(Instant.parse("2026-09-30T15:30:00Z"));

        assertCode(() -> use("2026-09-30"), "INVALID_DATE");
        assertThat(use("2026-10-01").days()).containsExactly(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("이번 달 마지막 날까지는 쓸 수 있다")
    void 이번_달_마지막_날까지는_쓸_수_있다() {
        assertThat(use("2026-09-30").days()).containsExactly(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("이미 쉬는 날이면 409 ALREADY_REST_DAY")
    void 이미_쉬는_날이면_ALREADY_REST_DAY() {
        use("2026-09-10");

        assertCode(() -> use("2026-09-10"), "ALREADY_REST_DAY");
    }

    @Test
    @DisplayName("그달 두 장을 다 쓰면 세 번째는 409 NO_REST_CARD_LEFT — 남은 카드는 다음 달로 넘어가지 않는다")
    void 두_장을_다_쓰면_NO_REST_CARD_LEFT() {
        use("2026-09-10");
        RestCardsView second = use("2026-09-11");

        assertThat(second.left()).isZero();
        assertCode(() -> use("2026-09-12"), "NO_REST_CARD_LEFT");
    }

    @Test
    @DisplayName("그날 운동한 아이가 있으면 422 ALREADY_MOVED — 보호자만 운동했으면 쓸 수 있다")
    void 그날_운동한_아이가_있으면_ALREADY_MOVED() {
        moved(sister, TODAY);
        moved(mom, LocalDate.of(2026, 9, 10));

        assertCode(() -> use("2026-09-09"), "ALREADY_MOVED");
        assertThat(use("2026-09-10").days()).containsExactly(LocalDate.of(2026, 9, 10));
    }

    @Test
    @DisplayName("검사 차례는 보호자 → 날짜 → 이미 쉬는 날 → 남은 카드 → 운동한 아이(FE 목과 같다)")
    void 검사_차례는_FE_목과_같다() {
        assertCode(() -> service.use(childUser, familyId, "2026-09-01"), "NOT_A_PARENT");

        use("2026-09-10");
        use("2026-09-11");
        moved(kid, LocalDate.of(2026, 9, 10));
        moved(kid, LocalDate.of(2026, 9, 12));
        assertCode(() -> use("2026-09-10"), "ALREADY_REST_DAY");
        assertCode(() -> use("2026-09-12"), "NO_REST_CARD_LEFT");
    }

    @Test
    @DisplayName("되돌린 카드는 돌아오고 그 번호를 다시 쓴다")
    void 되돌린_카드는_돌아오고_그_번호를_다시_쓴다() {
        use("2026-09-10");
        use("2026-09-11");

        RestCardsView afterCancel = service.cancel(parentUser, familyId, LocalDate.of(2026, 9, 10));
        assertThat(afterCancel.left()).isEqualTo(1);
        assertThat(afterCancel.days()).containsExactly(LocalDate.of(2026, 9, 11));

        RestCardsView again = use("2026-09-12");
        assertThat(again.left()).isZero();
        assertThat(cards.rows)
                .filteredOn(card -> card.restDate().equals(LocalDate.of(2026, 9, 12)))
                .singleElement()
                .extracting(RestCard::cardNo)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("다른 보호자가 같은 카드 번호를 먼저 넣으면 새로 읽어 남은 번호로 다시 쓴다")
    void 같은_번호를_뺏기면_남은_번호로_다시_쓴다() {
        cards.beforeNextInsert(() -> storeCard(LocalDate.of(2026, 9, 21), 1));

        RestCardsView view = use("2026-09-20");

        assertThat(cards.insertCalls).isEqualTo(2);
        assertThat(view.left()).isZero();
        assertThat(view.days()).containsExactly(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("다른 보호자가 마지막 카드를 먼저 쓰면 세 장이 되지 않고 409 NO_REST_CARD_LEFT")
    void 마지막_카드를_뺏기면_NO_REST_CARD_LEFT() {
        storeCard(LocalDate.of(2026, 9, 15), 1);
        cards.beforeNextInsert(() -> storeCard(LocalDate.of(2026, 9, 21), 2));

        assertCode(() -> use("2026-09-20"), "NO_REST_CARD_LEFT");
        assertThat(cards.rows).hasSize(2);
    }

    @Test
    @DisplayName("다른 보호자가 같은 날을 먼저 쓰면 409 ALREADY_REST_DAY")
    void 같은_날을_뺏기면_ALREADY_REST_DAY() {
        cards.beforeNextInsert(() -> storeCard(LocalDate.of(2026, 9, 20), 2));

        assertCode(() -> use("2026-09-20"), "ALREADY_REST_DAY");
        assertThat(cards.rows).hasSize(1);
    }

    @Test
    @DisplayName("세 번 연달아 자리를 뺏기면 더 시도하지 않고 409 CONFLICT — 우리 날은 들어가지 않는다")
    void 세_번_연달아_뺏기면_CONFLICT() {
        // 1회: 빈 1번을 고른 사이 다른 보호자가 9/21 에 1번을 쓴다.
        cards.beforeNextInsert(() -> storeCard(LocalDate.of(2026, 9, 21), 1));
        // 2회: 남은 2번을 고른 사이 그 보호자가 9/21 을 되돌리고 9/22 에 2번을 쓴다.
        cards.beforeNextInsert(() -> {
            removeCard(LocalDate.of(2026, 9, 21));
            storeCard(LocalDate.of(2026, 9, 22), 2);
        });
        // 3회: 다시 빈 1번을 고른 사이 9/23 에 1번을 쓴다.
        cards.beforeNextInsert(() -> storeCard(LocalDate.of(2026, 9, 23), 1));

        assertCode(() -> use("2026-09-20"), "CONFLICT");
        assertThat(cards.insertCalls).isEqualTo(RestCardService.MAX_ATTEMPTS);
        assertThat(cards.rows)
                .extracting(RestCard::restDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("되돌리기는 오늘과 앞날만 — 오늘 이미 운동했어도 되돌린다")
    void 되돌리기는_오늘과_앞날만() {
        use("2026-09-09");
        moved(kid, TODAY);

        RestCardsView view = service.cancel(parentUser, familyId, TODAY);

        assertThat(view.left()).isEqualTo(2);
        assertThat(view.days()).isEmpty();
    }

    @Test
    @DisplayName("지난 날은 422 INVALID_DATE · 쉬는 날이 아니면 404 NOT_REST_DAY · 아이는 403 NOT_A_PARENT")
    void 되돌릴_수_없는_경우() {
        storeCard(LocalDate.of(2026, 9, 8), 1);

        assertCode(() -> service.cancel(parentUser, familyId, LocalDate.of(2026, 9, 8)), "INVALID_DATE");
        assertCode(() -> service.cancel(parentUser, familyId, LocalDate.of(2026, 9, 10)), "NOT_REST_DAY");
        assertCode(() -> service.cancel(childUser, familyId, LocalDate.of(2026, 9, 10)), "NOT_A_PARENT");
        assertThat(cards.rows).hasSize(1);
    }

    @Test
    @DisplayName("RestDayQuery 는 달을 넘는 범위에서도 쉬는 날을 오름차순으로 준다")
    void RestDayQuery는_달을_넘는_범위도_준다() {
        storeCard(LocalDate.of(2026, 10, 2), 1);
        storeCard(LocalDate.of(2026, 9, 28), 1);
        storeCard(LocalDate.of(2026, 9, 1), 2);

        assertThat(service.restDaysBetween(familyId, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
                .containsExactly(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2));
        assertThatThrownBy(() -> service.restDaysBetween(familyId, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
