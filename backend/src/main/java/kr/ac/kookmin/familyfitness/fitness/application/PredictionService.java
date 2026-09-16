package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.application.port.PredictionRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessItem;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestNotFoundException;
import kr.ac.kookmin.familyfitness.fitness.domain.NoFitnessTestException;
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException;
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction;
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionScenario;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile;
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryRequest;
import kr.ac.kookmin.familyfitness.shared.ai.TrajectoryResponse;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * `AiGateway.trajectory` 를 불러 결과를 그대로 굳힌다. AI 장애({@link kr.ac.kookmin.familyfitness.shared.ai.AiUnavailableException})는
 * 잡지 않고 그대로 올린다(503). 외부 호출을 트랜잭션 안에 두지 않으려고 저장은 저장소 어댑터의 트랜잭션에 맡긴다.
 */
@Service
public class PredictionService {
    private final FitnessTestRepository tests;
    private final PredictionRepository predictions;
    private final AiGateway ai;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final Clock clock;
    private final ZoneId zone;

    public PredictionService(
            FitnessTestRepository tests,
            PredictionRepository predictions,
            AiGateway ai,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            Clock clock,
            ZoneId zone) {
        this.tests = tests;
        this.predictions = predictions;
        this.ai = ai;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.clock = clock;
        this.zone = zone;
    }

    public Prediction predict(UUID actorId, UUID profileId, PredictCommand command) {
        ProfileSummary summary = familyAccess.requireSameFamilyAsProfile(actorId, profileId);
        ProfileDetails details = profileQuery.findDetails(profileId);
        if (details == null) throw new ProfileNotFoundException(profileId);
        if (summary.consentRequired() && !summary.consentGiven()) throw new ConsentRequiredException();
        if (!summary.measurable()) throw new NotMeasurableException();

        FitnessItem item = FitnessItem.resolve(command.itemCode());
        FitnessTest test = baseTest(profileId, command.fitnessTestId());

        LocalDate today = LocalDate.now(clock.withZone(zone));
        int currentAgeYears = Ages.fullYears(details.birthDate(), today);
        AiProfile profile = aiProfile(profileId, details, test, today, currentAgeYears);

        TrajectoryResponse response =
                ai.trajectory(new TrajectoryRequest(profile, item.getCode(), command.horizonYears()));

        // ▲ 확정 필요 — AI bands[].age 는 만 나이(세)로 본다. 지금 나이보다 어린 구간은 버린다.
        Map<Integer, PredictionPoint> byYears = new LinkedHashMap<>();
        for (TrajectoryResponse.Band band : response.bands()) {
            if (band.age() < currentAgeYears) continue;
            int yearsFromNow = band.age() - currentAgeYears;
            byYears.putIfAbsent(
                    yearsFromNow,
                    new PredictionPoint(
                            PredictionScenario.MAINTAIN,
                            response.itemCode(),
                            yearsFromNow,
                            toBigDecimal(band.p10()),
                            toBigDecimal(band.p50()),
                            toBigDecimal(band.p90())));
        }
        List<PredictionPoint> points = List.copyOf(byYears.values());

        Prediction prediction = new Prediction(
                UUID.randomUUID(),
                profileId,
                test.getId(),
                item.getCode(),
                command.horizonYears(),
                Prediction.MODEL_VERSION,
                response.basis(),
                response.notice(),
                points,
                clock.instant());
        return predictions.save(prediction);
    }

    private FitnessTest baseTest(UUID profileId, @Nullable UUID fitnessTestId) {
        if (fitnessTestId == null) {
            FitnessTest latest = tests.findLatestByProfileId(profileId);
            if (latest == null) throw new NoFitnessTestException();
            return latest;
        }
        FitnessTest test = tests.findById(fitnessTestId);
        if (test == null || !test.getProfileId().equals(profileId)) {
            throw new FitnessTestNotFoundException(fitnessTestId);
        }
        return test;
    }

    /** 이름·생년월일·계정 식별자는 보내지 않는다. 유아기는 개월, 그 외는 세. 측정값은 회차 항목(005·006 은 애초에 없다). */
    private AiProfile aiProfile(
            UUID profileId, ProfileDetails details, FitnessTest test, LocalDate today, int currentAgeYears) {
        AgeGroup ageGroup = AgeGroup.ofAge(currentAgeYears);
        int age = ageGroup == AgeGroup.TODDLER ? Ages.fullMonths(details.birthDate(), today) : currentAgeYears;
        BigDecimal heightCm = test.getHeightCm() != null ? test.getHeightCm() : details.heightCm();
        BigDecimal weightKg = test.getWeightKg() != null ? test.getWeightKg() : details.weightKg();
        Map<String, Double> measurements = new LinkedHashMap<>();
        test.getMeasurements().forEach((code, value) -> measurements.put(code, value.doubleValue()));
        return new AiProfile(
                ProfileRef.of(profileId),
                age,
                ageGroup.getAgeUnit(),
                details.sex().name(),
                heightCm == null ? null : heightCm.doubleValue(),
                weightKg == null ? null : weightKg.doubleValue(),
                measurements);
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
