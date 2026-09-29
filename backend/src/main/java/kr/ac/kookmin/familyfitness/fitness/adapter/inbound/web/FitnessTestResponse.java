package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.application.RegisteredFitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;

public record FitnessTestResponse(
        UUID fitnessTestId,
        LocalDate testedOn,
        /** 그 회차에 같이 적은 체지방률 · 허리둘레. 안 적었으면 null. 등록은 보호자만 해서 늘 싣는다. */
        @Nullable BigDecimal bodyFatPct,
        @Nullable BigDecimal waistCm,
        List<ItemResult> items,
        @Nullable FactorPoint weakest,
        @Nullable FactorPoint strongest,
        /** 이 회차의 인증 등급(한 사람에게 하나). 등록은 보호자만 해서 늘 싣는다. */
        CertificationResponse certification,
        String disclaimer) {
    public static FitnessTestResponse of(RegisteredFitnessTest registered) {
        FitnessTest test = registered.test();
        return new FitnessTestResponse(
                test.getId(),
                test.getTestedOn(),
                test.getBodyFatPct(),
                test.getWaistCm(),
                ItemResult.of(test),
                test.getWeakest(),
                test.getStrongest(),
                CertificationResponse.of(registered.certification()),
                Copy.FITNESS_DISCLAIMER);
    }
}
