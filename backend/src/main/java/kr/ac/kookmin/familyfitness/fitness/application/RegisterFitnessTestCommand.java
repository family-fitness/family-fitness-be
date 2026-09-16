package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import org.jspecify.annotations.Nullable;

public record RegisterFitnessTestCommand(
        LocalDate testedOn,
        FitnessTestSource source,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg,
        List<Measurement> measurements) {}
