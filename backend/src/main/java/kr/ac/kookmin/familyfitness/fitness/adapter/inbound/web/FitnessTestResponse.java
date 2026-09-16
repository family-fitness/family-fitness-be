package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;

public record FitnessTestResponse(
        UUID fitnessTestId,
        LocalDate testedOn,
        List<ItemResult> items,
        @Nullable FactorPoint weakest,
        @Nullable FactorPoint strongest,
        String disclaimer) {
    public static FitnessTestResponse of(FitnessTest test) {
        return new FitnessTestResponse(
                test.getId(),
                test.getTestedOn(),
                ItemResult.of(test),
                test.getWeakest(),
                test.getStrongest(),
                Copy.FITNESS_DISCLAIMER);
    }
}
