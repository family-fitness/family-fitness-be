package kr.ac.kookmin.familyfitness.fitness.application;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.detailsOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.norms;
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
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.NormPair;
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException;
import kr.ac.kookmin.familyfitness.fitness.domain.DuplicateDateException;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestItem;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.FutureTestDateException;
import kr.ac.kookmin.familyfitness.fitness.domain.Grade;
import kr.ac.kookmin.familyfitness.fitness.domain.ItemNotForAgeGroupException;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import kr.ac.kookmin.familyfitness.fitness.domain.NoItemsException;
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
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

    /** 2026-09-09 12:00 KST */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneOffset.UTC);

    private final ZoneId zone = ZoneId.of("Asia/Seoul");
    private final LocalDate today = LocalDate.of(2026, 9, 9);

    private final InMemoryFitnessTestRepository tests = new InMemoryFitnessTestRepository();
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final ProfileQuery profileQuery = mock(ProfileQuery.class);
    private final NormCatalog norms = normCatalog();
    private final FitnessTestService service =
            new FitnessTestService(tests, norms, familyAccess, profileQuery, clock, zone);

    private static NormCatalog normCatalog() {
        List<NormPoint> points = new ArrayList<>(norms(
                "028",
                Sex.F,
                7,
                12,
                new NormPair(5, 22.0),
                new NormPair(25, 30.0),
                new NormPair(50, 36.0),
                new NormPair(75, 42.0),
                new NormPair(95, 52.0)));
        points.addAll(norms("012", Sex.F, 7, 12, new NormPair(5, -2.0), new NormPair(50, 9.0), new NormPair(95, 20.0)));
        NormCatalog catalog = new NormCatalog(new StaticNormRepository(points));
        catalog.refresh();
        return catalog;
    }

    private void childProfile() {
        childProfile(LocalDate.of(2017, 5, 1), true, true, true);
    }

    private void childProfile(LocalDate birthDate) {
        childProfile(birthDate, true, true, true);
    }

    private void childProfile(LocalDate birthDate, boolean consentRequired, boolean consentGiven, boolean measurable) {
        when(familyAccess.requireSameFamilyAsProfile(actorId, profileId))
                .thenReturn(summaryOf(profileId, familyId, AgeGroup.YOUTH, measurable, consentRequired, consentGiven));
        when(profileQuery.findDetails(profileId))
                .thenReturn(detailsOf(profileId, familyId, birthDate, Sex.F, null, null, true));
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
                testedOn, FitnessTestSource.SELF_INPUT, null, null, List.copyOf(measurements));
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
        when(familyAccess.requireSameFamilyAsProfile(actorId, profileId)).thenThrow(new NotSameFamilyException());
        assertThatThrownBy(() -> service.register(actorId, profileId, command(new ItemPair("028", 36))))
                .isInstanceOf(NotSameFamilyException.class);
        assertThat(tests.saved).isEmpty();
    }

    @Test
    @DisplayName("등록하면 백분위가 규준으로 계산돼 굳고 등급·구간이 붙는다")
    void 등록하면_백분위가_규준으로_계산돼_굳고_등급_구간이_붙는다() {
        childProfile();
        FitnessTest test =
                service.register(actorId, profileId, command(new ItemPair("028", 42), new ItemPair("012", 9)));

        assertThat(test.getAgeAtTest()).isEqualTo(9);
        assertThat(test.getAgeGroup()).isEqualTo(AgeGroup.YOUTH);
        FitnessTestItem grip = itemOf(test, "028");
        assertThat(grip.percentile()).isEqualTo(75);
        assertThat(grip.score().grade()).isEqualTo(Grade.SECOND);
        assertThat(grip.score().band()).isEqualTo(Band.STRENGTH);
        assertThat(itemOf(test, "012").percentile()).isEqualTo(50);
        assertThat(test.getWeakest().itemCode()).isEqualTo("012");
        assertThat(test.getStrongest().itemCode()).isEqualTo("028");
        assertThat(tests.saved).containsKey(test.getId());

        // 규준표가 바뀌어도 저장된 값은 그대로다
        assertThat(itemOf(tests.findById(test.getId()), "028").percentile()).isEqualTo(75);
    }

    @Test
    @DisplayName("규준 없는 항목은 백분위 null 로 저장된다")
    void 규준_없는_항목은_백분위_null_로_저장된다() {
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
        assertThat(service.latest(actorId, profileId)).isNull();
        service.register(actorId, profileId, command(LocalDate.of(2026, 8, 1), new ItemPair("028", 30)));
        FitnessTest newer =
                service.register(actorId, profileId, command(LocalDate.of(2026, 9, 1), new ItemPair("028", 40)));
        assertThat(service.latest(actorId, profileId).getId()).isEqualTo(newer.getId());
    }

    @Test
    @DisplayName("측정 이력은 testedOn 이 늦은 회차부터 size 개이고 없으면 빈 목록")
    void 측정_이력은_testedOn_이_늦은_회차부터_size_개이고_없으면_빈_목록() {
        childProfile();
        assertThat(service.history(actorId, profileId, 20)).isEmpty();

        FitnessTest august =
                service.register(actorId, profileId, command(LocalDate.of(2026, 8, 1), new ItemPair("028", 30)));
        FitnessTest september =
                service.register(actorId, profileId, command(LocalDate.of(2026, 9, 1), new ItemPair("028", 40)));
        FitnessTest july =
                service.register(actorId, profileId, command(LocalDate.of(2026, 7, 1), new ItemPair("028", 36)));

        assertThat(service.history(actorId, profileId, 20))
                .extracting(FitnessTest::getId)
                .containsExactly(september.getId(), august.getId(), july.getId());
        assertThat(service.history(actorId, profileId, 2))
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
        assertThat(service.history(actorId, profileId, FitnessTestService.HISTORY_MAX_SIZE))
                .isEmpty();
    }

    @Test
    @DisplayName("측정 이력도 같은 가족이 아니면 identity 의 예외가 그대로 올라간다")
    void 측정_이력도_같은_가족이_아니면_identity_의_예외가_그대로_올라간다() {
        when(familyAccess.requireSameFamilyAsProfile(actorId, profileId)).thenThrow(new NotSameFamilyException());
        assertThatThrownBy(() -> service.history(actorId, profileId, 20)).isInstanceOf(NotSameFamilyException.class);
    }
}
