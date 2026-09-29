package kr.ac.kookmin.familyfitness.fitness.application;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.childAccountOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.detailsOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.parentOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.peers;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.summaryOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered.Round;
import kr.ac.kookmin.familyfitness.fitness.domain.BodyMeasures;
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException;
import kr.ac.kookmin.familyfitness.fitness.domain.DuplicateDateException;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestItem;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.FutureTestDateException;
import kr.ac.kookmin.familyfitness.fitness.domain.Grade;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeThreshold;
import kr.ac.kookmin.familyfitness.fitness.domain.ItemNotForAgeGroupException;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import kr.ac.kookmin.familyfitness.fitness.domain.NoItemsException;
import kr.ac.kookmin.familyfitness.fitness.domain.NormAgeUnit;
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException;
import kr.ac.kookmin.familyfitness.fitness.domain.ThresholdOp;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FitnessTestServiceTest {
    private final UUID actorId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();

    /** 2026-09-09 12:00 KST */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneOffset.UTC);

    private final ZoneId zone = ZoneId.of("Asia/Seoul");
    private final LocalDate today = LocalDate.of(2026, 9, 9);

    private final InMemoryFitnessTestRepository tests = new InMemoryFitnessTestRepository();
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final ProfileQuery profileQuery = mock(ProfileQuery.class);
    private final PeerCatalog peers = peerCatalog();
    private final GradeCatalog grades = gradeCatalog();
    private final List<Object> published = new ArrayList<>();
    private final FitnessTestService service =
            new FitnessTestService(tests, peers, grades, familyAccess, profileQuery, published::add, clock, zone);

    /** 여아 기준 몇 줄. 유소년 028 은 7~12세 한 구간, 유아기 020 은 개월 구간 둘(48~53 · 54~59)이다. */
    private static GradeCatalog gradeCatalog() {
        List<GradeThreshold> rows = List.of(
                threshold(AgeGroup.YOUTH, NormAgeUnit.YEARS, 7, 12, Grade.FIRST, "028", "45"),
                threshold(AgeGroup.YOUTH, NormAgeUnit.YEARS, 7, 12, Grade.SECOND, "028", "40"),
                threshold(AgeGroup.YOUTH, NormAgeUnit.YEARS, 7, 12, Grade.THIRD, "028", "35"),
                threshold(AgeGroup.TODDLER, NormAgeUnit.MONTHS, 48, 53, Grade.FIRST, "020", "48"),
                threshold(AgeGroup.TODDLER, NormAgeUnit.MONTHS, 54, 59, Grade.FIRST, "020", "59"),
                threshold(AgeGroup.TODDLER, NormAgeUnit.MONTHS, 54, 59, Grade.SECOND, "020", "44"));
        GradeCatalog catalog = new GradeCatalog(() -> rows);
        catalog.refresh();
        return catalog;
    }

    private static GradeThreshold threshold(
            AgeGroup ageGroup, NormAgeUnit unit, int from, int to, Grade grade, String itemCode, String cutoff) {
        return new GradeThreshold(
                ageGroup, Sex.F, unit, from, to, grade, itemCode, ThresholdOp.AT_LEAST, new BigDecimal(cutoff), null);
    }

    /** 여아 만 9세(유소년) 또래 분포 두 칸. 028 은 백분위 i 의 값이 i - 33, 012 는 i - 41 이다. */
    private static PeerCatalog peerCatalog() {
        PeerCatalog catalog = new PeerCatalog(() -> List.of(
                peers(AgeGroup.YOUTH, Sex.F, 9, "028", -33, 1), peers(AgeGroup.YOUTH, Sex.F, 9, "012", -41, 1)));
        catalog.refresh();
        return catalog;
    }

    private void childProfile() {
        childProfile(LocalDate.of(2017, 5, 1), true, true, true);
    }

    private void childProfile(LocalDate birthDate) {
        childProfile(birthDate, true, true, true);
    }

    /** 대상은 아이 프로필, 호출 계정은 같은 가족의 보호자다. */
    private void childProfile(LocalDate birthDate, boolean consentRequired, boolean consentGiven, boolean measurable) {
        when(familyAccess.requireSameFamilyAsProfile(actorId, profileId))
                .thenReturn(summaryOf(profileId, familyId, AgeGroup.YOUTH, measurable, consentRequired, consentGiven));
        when(familyAccess.requireParentOfProfile(actorId, profileId)).thenReturn(parentOf(parentId, familyId));
        when(familyAccess.requireMember(actorId, familyId)).thenReturn(parentOf(parentId, familyId));
        when(profileQuery.findDetails(profileId))
                .thenReturn(detailsOf(profileId, familyId, birthDate, Sex.F, null, null, true));
    }

    /** 같은 조건에서 호출 계정만 자녀(CHILD) 본인 계정으로 바꾼다. */
    private void callerIsChildAccount() {
        when(familyAccess.requireParentOfProfile(actorId, profileId)).thenThrow(new NotAParentException());
        when(familyAccess.requireMember(actorId, familyId)).thenReturn(childAccountOf(UUID.randomUUID(), familyId));
    }

    private record ItemPair(String code, int value) {}

    private RegisterFitnessTestCommand command(ItemPair... items) {
        return command(LocalDate.of(2026, 9, 1), items);
    }

    private RegisterFitnessTestCommand command(LocalDate testedOn, ItemPair... items) {
        List<Measurement> measurements = new ArrayList<>();
        for (ItemPair item : items) {
            measurements.add(new Measurement(item.code(), BigDecimal.valueOf(item.value())));
        }
        return new RegisterFitnessTestCommand(
                testedOn, FitnessTestSource.SELF_INPUT, BodyMeasures.NONE, List.copyOf(measurements));
    }

    private static FitnessTestItem itemOf(FitnessTest test, String code) {
        return test.getItems().stream()
                .filter(it -> it.item().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("같은 가족이 아니면 identity 의 예외가 그대로 올라간다")
    void 같은_가족이_아니면_identity_의_예외가_그대로_올라간다() {
        when(familyAccess.requireParentOfProfile(actorId, profileId)).thenThrow(new NotSameFamilyException());
        when(familyAccess.requireSameFamilyAsProfile(actorId, profileId)).thenThrow(new NotSameFamilyException());
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 36))))
                .isInstanceOf(NotSameFamilyException.class);
        assertThat(tests.saved).isEmpty();
    }

    @Test
    @DisplayName("등록하면 백분위가 또래 분포로, 등급이 공식 기준표로 계산돼 굳고 구간이 붙는다")
    void 등록하면_백분위가_규준으로_등급이_기준표로_계산돼_굳고_구간이_붙는다() {
        childProfile();
        FitnessTest test =
                service.register(actorId, profileId, command(new ItemPair("028", 42), new ItemPair("012", 9)));

        assertThat(test.getAgeAtTest()).isEqualTo(9);
        assertThat(test.getAgeGroup()).isEqualTo(AgeGroup.YOUTH);
        // 42 는 75번째 칸 하나 — 아래 · 위 자리 75 · 76 의 가운데 75.5 를 짝수 쪽으로 반올림해 76(AI percentile_of 와 같다)
        FitnessTestItem grip = itemOf(test, "028");
        assertThat(grip.percentile()).isEqualTo(76);
        assertThat(grip.score().grade()).isEqualTo(Grade.SECOND);
        assertThat(grip.score().band()).isEqualTo(Band.STRENGTH);
        assertThat(itemOf(test, "012").percentile()).isEqualTo(50);
        // 012 는 이 기준표에 줄이 없어 등급이 null 이다(백분위와 따로다)
        assertThat(itemOf(test, "012").score().grade()).isNull();
        assertThat(test.getWeakest().itemCode()).isEqualTo("012");
        assertThat(test.getStrongest().itemCode()).isEqualTo("028");
        assertThat(tests.saved).containsKey(test.getId());

        // 저장된 값을 그대로 읽는다
        assertThat(itemOf(tests.findById(test.getId()), "028").percentile()).isEqualTo(76);
    }

    @Test
    @DisplayName("키 · 몸무게 · 체지방률 · 허리둘레는 그 회차에 같이 굳고, 측정 항목(getMeasurements)에는 섞이지 않는다")
    void 신체_값은_그_회차에_같이_굳는다() {
        childProfile();
        BodyMeasures body = new BodyMeasures(
                new BigDecimal("135.5"), new BigDecimal("31.2"), new BigDecimal("22.5"), new BigDecimal("61.2"));
        FitnessTest test = service.register(
                actorId,
                profileId,
                new RegisterFitnessTestCommand(
                        LocalDate.of(2026, 9, 1),
                        FitnessTestSource.SELF_INPUT,
                        body,
                        List.of(new Measurement("028", BigDecimal.valueOf(36)))));

        FitnessTest stored = tests.findById(test.getId());
        assertThat(stored.getBody()).isEqualTo(body);
        assertThat(stored.getHeightCm()).isEqualByComparingTo("135.5");
        assertThat(stored.getWeightKg()).isEqualByComparingTo("31.2");
        assertThat(stored.getBodyFatPct()).isEqualByComparingTo("22.5");
        assertThat(stored.getWaistCm()).isEqualByComparingTo("61.2");
        assertThat(stored.getMeasurements()).containsOnlyKeys("028");
    }

    @Test
    @DisplayName("저장한 회차마다 FitnessTestRegistered 를 한 번 낸다 — 다시 잰 회차는 등록 순서가 아니라 testedOn 이 가장 이른 회차를 뺀 것")
    void 저장한_회차마다_FitnessTestRegistered_를_낸다() {
        childProfile();
        LocalDate sep1 = LocalDate.of(2026, 9, 1);
        LocalDate sep8 = LocalDate.of(2026, 9, 8);
        LocalDate aug1 = LocalDate.of(2026, 8, 1);
        LocalDate aug15 = LocalDate.of(2026, 8, 15);
        FitnessTest first = service.register(actorId, profileId, command(sep1, new ItemPair("028", 36)));
        FitnessTest later = service.register(actorId, profileId, command(sep8, new ItemPair("028", 40)));
        // 지난 날짜를 나중에 적으면 이 회차가 가장 이른 회차가 되고, 그때까지 가장 이르던 9/1 이 다시 잰 회차가 된다
        FitnessTest past = service.register(actorId, profileId, command(aug1, new ItemPair("028", 30)));
        // 가장 이른 날과 가장 늦은 날 사이에 적으면 새 회차 자신이다
        FitnessTest between = service.register(actorId, profileId, command(aug15, new ItemPair("028", 33)));

        assertThat(published)
                .containsExactly(
                        new FitnessTestRegistered(profileId, first.getId(), sep1, null),
                        new FitnessTestRegistered(profileId, later.getId(), sep8, new Round(later.getId(), sep8)),
                        new FitnessTestRegistered(profileId, past.getId(), aug1, new Round(first.getId(), sep1)),
                        new FitnessTestRegistered(
                                profileId, between.getId(), aug15, new Round(between.getId(), aug15)));
        // FE 목(tests.slice(0, -1))과 같이, 다시 잰 회차는 가장 이른 8/1 을 뺀 셋이다
        assertThat(published.stream()
                        .map(it -> ((FitnessTestRegistered) it).remeasured())
                        .filter(Objects::nonNull)
                        .map(Round::testedOn))
                .containsExactlyInAnyOrder(sep1, sep8, aug15);
    }

    @Test
    @DisplayName("막힌 등록은 이벤트를 내지 않는다")
    void 막힌_등록은_이벤트를_내지_않는다() {
        childProfile(LocalDate.of(2017, 5, 1), true, false, false);
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 36))))
                .isInstanceOf(ConsentRequiredException.class);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("또래 분포가 없는 항목은 백분위 null 로 저장된다")
    void 또래_분포가_없는_항목은_백분위_null_로_저장된다() {
        childProfile();
        FitnessTest test = service.register(actorId, profileId, command(new ItemPair("009", 20)));
        assertThat(test.getItems())
                .singleElement()
                .extracting(FitnessTestItem::percentile)
                .isNull();
        assertThat(test.getWeakest()).isNull();
    }

    @Test
    @DisplayName("동의가 필요한데 없거나 철회됐으면 CONSENT_REQUIRED")
    void 동의가_필요한데_없거나_철회됐으면_CONSENT_REQUIRED() {
        childProfile(LocalDate.of(2017, 5, 1), true, false, false);
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 36))))
                .isInstanceOf(ConsentRequiredException.class);
        assertThat(tests.saved).isEmpty();
    }

    @Test
    @DisplayName("동의 불필요(만 14세 이상)면 동의 없이도 저장한다")
    void 동의_불필요_만_14세_이상_면_동의_없이도_저장한다() {
        childProfile(LocalDate.of(2010, 1, 1), false, false, true);
        FitnessTest test = service.register(actorId, profileId, command(new ItemPair("028", 36)));
        assertThat(test.getAgeGroup()).isEqualTo(AgeGroup.ADOLESCENT);
    }

    @Test
    @DisplayName("측정일 기준 만 4세 미만이면 NOT_MEASURABLE")
    void 측정일_기준_만_4세_미만이면_NOT_MEASURABLE() {
        childProfile(LocalDate.of(2023, 1, 1), true, true, false);
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 36))))
                .isInstanceOf(NotMeasurableException.class);
    }

    @Test
    @DisplayName("측정일이 미래면 400")
    void 측정일이_미래면_400() {
        childProfile();
        assertThatThrownBy(
                        () -> service.register(actorId, profileId, command(today.plusDays(1), new ItemPair("028", 36))))
                .isInstanceOf(FutureTestDateException.class);
        service.register(actorId, profileId, command(today, new ItemPair("028", 36)));
    }

    @Test
    @DisplayName("같은 날짜 측정이 이미 있으면 DUPLICATE_DATE")
    void 같은_날짜_측정이_이미_있으면_DUPLICATE_DATE() {
        childProfile();
        service.register(actorId, profileId, command(new ItemPair("028", 36)));
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 37))))
                .isInstanceOf(DuplicateDateException.class);
        assertThat(tests.saved).hasSize(1);
    }

    @Test
    @DisplayName("항목 규칙 위반은 저장하지 않는다")
    void 항목_규칙_위반은_저장하지_않는다() {
        childProfile();
        assertThatThrownBy(() -> service.register(actorId, profileId, command()))
                .isInstanceOf(NoItemsException.class);
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("013", 20))))
                .isInstanceOf(ItemNotForAgeGroupException.class);
        assertThat(tests.saved).isEmpty();
    }

    @Test
    @DisplayName("최신 회차는 testedOn 이 가장 늦은 것이고 없으면 null")
    void 최신_회차는_testedOn_이_가장_늦은_것이고_없으면_null() {
        childProfile();
        assertThat(service.latest(actorId, profileId).test()).isNull();
        service.register(actorId, profileId, command(LocalDate.of(2026, 8, 1), new ItemPair("028", 30)));
        FitnessTest newer =
                service.register(actorId, profileId, command(LocalDate.of(2026, 9, 1), new ItemPair("028", 40)));
        assertThat(service.latest(actorId, profileId).test().getId()).isEqualTo(newer.getId());
    }

    @Test
    @DisplayName("측정 이력은 testedOn 이 늦은 회차부터 size 개이고 없으면 빈 목록")
    void 측정_이력은_testedOn_이_늦은_회차부터_size_개이고_없으면_빈_목록() {
        childProfile();
        assertThat(service.history(actorId, profileId, 20).tests()).isEmpty();

        FitnessTest august =
                service.register(actorId, profileId, command(LocalDate.of(2026, 8, 1), new ItemPair("028", 30)));
        FitnessTest september =
                service.register(actorId, profileId, command(LocalDate.of(2026, 9, 1), new ItemPair("028", 40)));
        FitnessTest july =
                service.register(actorId, profileId, command(LocalDate.of(2026, 7, 1), new ItemPair("028", 36)));

        assertThat(service.history(actorId, profileId, 20).tests())
                .extracting(FitnessTest::getId)
                .containsExactly(september.getId(), august.getId(), july.getId());
        assertThat(service.history(actorId, profileId, 2).tests())
                .extracting(FitnessTest::getId)
                .containsExactly(september.getId(), august.getId());
    }

    @Test
    @DisplayName("측정 이력 size 는 1~100 이고 밖이면 400(IllegalArgumentException)")
    void 측정_이력_size_는_1_100_이고_밖이면_400() {
        childProfile();
        assertThatThrownBy(() -> service.history(actorId, profileId, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.history(actorId, profileId, FitnessTestService.HISTORY_MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.history(actorId, profileId, FitnessTestService.HISTORY_MAX_SIZE)
                        .tests())
                .isEmpty();
    }

    @Test
    @DisplayName("측정 이력도 같은 가족이 아니면 identity 의 예외가 그대로 올라간다")
    void 측정_이력도_같은_가족이_아니면_identity_의_예외가_그대로_올라간다() {
        when(familyAccess.requireSameFamilyAsProfile(actorId, profileId)).thenThrow(new NotSameFamilyException());
        assertThatThrownBy(() -> service.history(actorId, profileId, 20)).isInstanceOf(NotSameFamilyException.class);
    }

    @Test
    @DisplayName("자녀 계정은 남의 측정도 자기 측정도 등록하지 못한다(NOT_A_PARENT)")
    void 자녀_계정은_남의_측정도_자기_측정도_등록하지_못한다() {
        childProfile();
        callerIsChildAccount();
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 36))))
                .isInstanceOf(NotAParentException.class);
        assertThat(tests.saved).isEmpty();
    }

    @Test
    @DisplayName("latest · 이력은 부모 계정이면 parentScope=true, 자녀 계정이면 false 로 돌려준다")
    void latest_이력은_부모_계정이면_parentScope_true_자녀_계정이면_false() {
        childProfile();
        service.register(actorId, profileId, command(new ItemPair("028", 36)));
        assertThat(service.latest(actorId, profileId).parentScope()).isTrue();
        assertThat(service.history(actorId, profileId, 20).parentScope()).isTrue();

        callerIsChildAccount();
        LatestFitnessView latest = service.latest(actorId, profileId);
        assertThat(latest.parentScope()).isFalse();
        assertThat(latest.test()).isNotNull();
        FitnessHistoryView history = service.history(actorId, profileId, 20);
        assertThat(history.parentScope()).isFalse();
        assertThat(history.tests()).hasSize(1);
    }

    @Test
    @DisplayName("항목표 연령대는 측정일 기준 만 나이로 정한다 — 13세 생일 전 날짜면 유소년, 뒤면 청소년")
    void 항목표_연령대는_측정일_기준_만_나이로_정한다() {
        // 2013-09-05 생 — 오늘(2026-09-09)은 만 13세(청소년), 2026-09-04 는 만 12세(유소년)
        childProfile(LocalDate.of(2013, 9, 5));
        assertThat(service.ageGroupOn(actorId, profileId, LocalDate.of(2026, 9, 4)))
                .isEqualTo(AgeGroup.YOUTH);
        assertThat(service.ageGroupOn(actorId, profileId, LocalDate.of(2026, 9, 5)))
                .isEqualTo(AgeGroup.ADOLESCENT);
        assertThat(service.ageGroupOn(actorId, profileId, null)).isEqualTo(AgeGroup.ADOLESCENT);

        // 항목표와 등록 검사가 같은 셈이다 — 유소년 날짜에 유소년 항목 043 은 저장되고 청소년 항목 010 은 거절된다
        service.register(actorId, profileId, command(LocalDate.of(2026, 9, 4), new ItemPair("043", 30)));
        assertThatThrownBy(() -> service.register(
                        actorId, profileId, command(LocalDate.of(2026, 9, 3), new ItemPair("010", 30))))
                .isInstanceOf(ItemNotForAgeGroupException.class);
    }

    @Test
    @DisplayName("유아기 등급은 측정일 기준 개월로 기준 구간을 고른다 — 같은 만 4세라도 53개월과 54개월은 다른 줄")
    void 유아기_등급은_측정일_기준_개월로_기준_구간을_고른다() {
        // 2022-03-01 생 — 2026-08-15 는 53개월, 2026-09-01 은 54개월(둘 다 만 4세)
        childProfile(LocalDate.of(2022, 3, 1));
        FitnessTest at53 =
                service.register(actorId, profileId, command(LocalDate.of(2026, 8, 15), new ItemPair("020", 50)));
        FitnessTest at54 =
                service.register(actorId, profileId, command(LocalDate.of(2026, 9, 1), new ItemPair("020", 50)));

        assertThat(at53.getAgeGroup()).isEqualTo(AgeGroup.TODDLER);
        assertThat(itemOf(at53, "020").score().grade()).isEqualTo(Grade.FIRST);
        assertThat(itemOf(at54, "020").score().grade()).isEqualTo(Grade.SECOND);
    }

    @Test
    @DisplayName("항목표 연령대 — 미래 날짜 400, 만 4세 미만 NOT_MEASURABLE, 자녀 계정 NOT_A_PARENT")
    void 항목표_연령대_미래_날짜_만_4세_미만_자녀_계정() {
        childProfile(LocalDate.of(2022, 12, 1));
        assertThatThrownBy(() -> service.ageGroupOn(actorId, profileId, today.plusDays(1)))
                .isInstanceOf(FutureTestDateException.class);
        // 2026-09-09 는 만 3세
        assertThatThrownBy(() -> service.ageGroupOn(actorId, profileId, today))
                .isInstanceOf(NotMeasurableException.class);

        callerIsChildAccount();
        assertThatThrownBy(() -> service.ageGroupOn(actorId, profileId, today)).isInstanceOf(NotAParentException.class);
    }
}
