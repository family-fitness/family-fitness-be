package kr.ac.kookmin.familyfitness.fitness.application;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.detailsOf;
import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.summaryOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestNotFoundException;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.ItemNotAllowedException;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import kr.ac.kookmin.familyfitness.fitness.domain.NoFitnessTestException;
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction;
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionScenario;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException;
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryRequest;
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryResponse;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PredictionServiceTest {
    private final UUID actorId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    /** 2026-09-09 12:00 KST */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneOffset.UTC);

    private final ZoneId zone = ZoneId.of("Asia/Seoul");

    private final InMemoryFitnessTestRepository tests = new InMemoryFitnessTestRepository();
    private final InMemoryPredictionRepository predictions = new InMemoryPredictionRepository();
    private final AiGateway ai = mock(AiGateway.class);
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final ProfileQuery profileQuery = mock(ProfileQuery.class);
    private final PredictionService service =
            new PredictionService(tests, predictions, ai, familyAccess, profileQuery, clock, zone);

    private void profile() {
        profile(LocalDate.of(2017, 5, 1), true, true);
    }

    private void profile(LocalDate birthDate) {
        profile(birthDate, true, true);
    }

    private void profile(LocalDate birthDate, boolean consentGiven, boolean measurable) {
        AgeGroup ageGroup = AgeGroup.of(birthDate, LocalDate.of(2026, 9, 9));
        ProfileSummary summary = summaryOf(profileId, familyId, ageGroup, measurable, true, consentGiven);
        when(familyAccess.requireActingAs(actorId, profileId)).thenReturn(summary);
        when(profileQuery.findDetails(profileId))
                .thenReturn(detailsOf(
                        profileId, familyId, birthDate, Sex.F, new BigDecimal("135.0"), new BigDecimal("31.5"), true));
    }

    private FitnessTest savedTest() {
        return savedTest(LocalDate.of(2026, 9, 1), 9, null);
    }

    private FitnessTest savedTest(LocalDate testedOn) {
        return savedTest(testedOn, 9, null);
    }

    private FitnessTest savedTest(LocalDate testedOn, int ageAtTest, @Nullable BigDecimal heightCm) {
        return tests.save(FitnessTest.register(
                UUID.randomUUID(),
                profileId,
                testedOn,
                FitnessTestSource.SELF_INPUT,
                ageAtTest,
                heightCm,
                null,
                List.of(new Measurement("028", new BigDecimal("40.5")), new Measurement("012", new BigDecimal("9"))),
                (item, value) -> 60,
                clock.instant()));
    }

    private static TrajectoryResponse aiResponse(TrajectoryResponse.Band... bands) {
        return new TrajectoryResponse(
                "cross_sectional_group_distribution",
                "028",
                "상대악력",
                "%",
                List.of(bands),
                "집단 분포를 바탕으로 한 참고 범위입니다. 개인의 변화를 나타내지 않습니다.",
                false);
    }

    private static TrajectoryResponse.Band band(int age, double p50) {
        return new TrajectoryResponse.Band(age, p50 - 5, p50, p50 + 5, 100);
    }

    @Test
    @DisplayName("대신할 수 없는 프로필(계정이 붙은 다른 식구)의 예측은 만들지 못한다 — 같은 가족이어도 CannotActAsProfile")
    void 대신할_수_없는_프로필의_예측은_만들지_못한다() {
        profile();
        savedTest();
        when(familyAccess.requireActingAs(actorId, profileId))
                .thenThrow(new CannotActAsProfileException("내 프로필이나 계정 없는 아이 프로필로만 할 수 있습니다"));

        assertThatThrownBy(() -> service.predict(actorId, profileId, new PredictCommand()))
                .isInstanceOf(CannotActAsProfileException.class);
        verifyNoInteractions(ai);
        assertThat(predictions.saved).isEmpty();
    }

    @Test
    @DisplayName("측정 기록이 없으면 NO_FITNESS_TEST")
    void 측정_기록이_없으면_NO_FITNESS_TEST() {
        profile();
        assertThatThrownBy(() -> service.predict(actorId, profileId, new PredictCommand()))
                .isInstanceOf(NoFitnessTestException.class);
    }

    @Test
    @DisplayName("동의가 철회됐으면 CONSENT_REQUIRED")
    void 동의가_철회됐으면_CONSENT_REQUIRED() {
        profile(LocalDate.of(2017, 5, 1), false, false);
        savedTest();
        assertThatThrownBy(() -> service.predict(actorId, profileId, new PredictCommand()))
                .isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    @DisplayName("혈압 항목으로는 예측하지 않는다")
    void 혈압_항목으로는_예측하지_않는다() {
        profile();
        savedTest();
        assertThatThrownBy(() -> service.predict(
                        actorId, profileId, new PredictCommand(null, PredictCommand.DEFAULT_HORIZON_YEARS, "005")))
                .isInstanceOf(ItemNotAllowedException.class);
    }

    @Test
    @DisplayName("AI 를 부르고 지금 나이 이후 구간만 MAINTAIN 포인트로 저장한다")
    void AI_를_부르고_지금_나이_이후_구간만_MAINTAIN_포인트로_저장한다() {
        profile();
        FitnessTest test = savedTest(LocalDate.of(2026, 9, 1), 9, new BigDecimal("136.2"));
        when(ai.trajectory(any())).thenReturn(aiResponse(band(8, 35.0), band(9, 38.0), band(12, 45.0), band(19, 60.0)));

        Prediction prediction = service.predict(actorId, profileId, new PredictCommand(null, 10, "028"));

        ArgumentCaptor<TrajectoryRequest> captor = ArgumentCaptor.forClass(TrajectoryRequest.class);
        verify(ai).trajectory(captor.capture());
        TrajectoryRequest request = captor.getValue();
        assertThat(request.itemCode()).isEqualTo("028");
        assertThat(request.horizonYears()).isEqualTo(10);
        assertThat(request.profile().profileRef()).isEqualTo(ProfileRef.of(profileId));
        assertThat(request.profile().age()).isEqualTo(9);
        assertThat(request.profile().ageUnit()).isEqualTo("세");
        assertThat(request.profile().sex()).isEqualTo("F");
        assertThat(request.profile().heightCm()).isEqualTo(136.2);
        assertThat(request.profile().weightKg()).isEqualTo(31.5);
        assertThat(request.profile().measurements())
                .containsExactlyInAnyOrderEntriesOf(Map.of("028", 40.5, "012", 9.0));
        assertThat(request.profile().inputLevel()).isEqualTo("L2");

        assertThat(prediction.getFitnessTestId()).isEqualTo(test.getId());
        assertThat(prediction.getModelVersion()).isEqualTo(Prediction.MODEL_VERSION);
        assertThat(prediction.getBasis()).isEqualTo("cross_sectional_group_distribution");
        assertThat(prediction.getHorizonYears()).isEqualTo(10);
        assertThat(prediction.getPoints())
                .allMatch(it -> it.scenario() == PredictionScenario.MAINTAIN
                        && it.itemCode().equals("028"));
        assertThat(prediction.getPoints().stream()
                        .map(PredictionPoint::yearsFromNow)
                        .toList())
                .containsExactly(0, 3, 10);
        assertThat(prediction.getPoints().get(1).p50()).isEqualByComparingTo(new BigDecimal("45.0"));
        assertThat(prediction.getPoints().get(1).p10()).isEqualByComparingTo(new BigDecimal("40.0"));
        assertThat(prediction.getPoints().get(1).p90()).isEqualByComparingTo(new BigDecimal("50.0"));
        assertThat(predictions.saved).containsExactly(prediction);
    }

    @Test
    @DisplayName("유아기는 개월 단위로 보낸다")
    void 유아기는_개월_단위로_보낸다() {
        profile(LocalDate.of(2021, 3, 9));
        savedTest(LocalDate.of(2026, 9, 1), 5, null);
        when(ai.trajectory(any())).thenReturn(aiResponse(band(5, 20.0)));

        service.predict(actorId, profileId, new PredictCommand());

        ArgumentCaptor<TrajectoryRequest> captor = ArgumentCaptor.forClass(TrajectoryRequest.class);
        verify(ai).trajectory(captor.capture());
        assertThat(captor.getValue().profile().age()).isEqualTo(66);
        assertThat(captor.getValue().profile().ageUnit()).isEqualTo("개월");
    }

    @Test
    @DisplayName("fitnessTestId 를 주면 그 회차를 쓰고 다른 프로필 것이면 NOT_FOUND")
    void fitnessTestId_를_주면_그_회차를_쓰고_다른_프로필_것이면_NOT_FOUND() {
        profile();
        FitnessTest older = savedTest(LocalDate.of(2026, 7, 1));
        savedTest(LocalDate.of(2026, 9, 1));
        when(ai.trajectory(any())).thenReturn(aiResponse(band(10, 40.0)));

        Prediction prediction = service.predict(
                actorId,
                profileId,
                new PredictCommand(
                        older.getId(), PredictCommand.DEFAULT_HORIZON_YEARS, PredictCommand.DEFAULT_ITEM_CODE));
        assertThat(prediction.getFitnessTestId()).isEqualTo(older.getId());

        assertThatThrownBy(() -> service.predict(
                        actorId,
                        profileId,
                        new PredictCommand(
                                UUID.randomUUID(),
                                PredictCommand.DEFAULT_HORIZON_YEARS,
                                PredictCommand.DEFAULT_ITEM_CODE)))
                .isInstanceOf(FitnessTestNotFoundException.class);
    }

    @Test
    @DisplayName("AI 장애는 잡지 않고 그대로 올린다")
    void AI_장애는_잡지_않고_그대로_올린다() {
        profile();
        savedTest();
        when(ai.trajectory(any())).thenThrow(new AiUnavailableException("timeout"));
        assertThatThrownBy(() -> service.predict(actorId, profileId, new PredictCommand()))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(predictions.saved).isEmpty();
    }
}
