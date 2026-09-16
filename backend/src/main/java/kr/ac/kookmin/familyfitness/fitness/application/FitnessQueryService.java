package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.Collection;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈(coaching)이 읽는 최신 측정 요약. 권한 판단은 호출 모듈의 책임이다. */
@Service
public class FitnessQueryService implements FitnessQuery {
    private final FitnessTestRepository tests;

    public FitnessQueryService(FitnessTestRepository tests) {
        this.tests = tests;
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable LatestFitness latestOf(UUID profileId) {
        FitnessTest test = tests.findLatestByProfileId(profileId);
        if (test == null) return null;
        return new LatestFitness(
                test.getProfileId(),
                test.getId(),
                test.getTestedOn(),
                test.getHeightCm(),
                test.getWeightKg(),
                test.getMeasurements(),
                test.getWeakest(),
                test.getStrongest());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyTest(Collection<UUID> profileIds) {
        return !profileIds.isEmpty() && tests.existsByProfileIdIn(profileIds);
    }
}
