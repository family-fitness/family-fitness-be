package kr.ac.kookmin.familyfitness.fitness.application;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection;
import org.jspecify.annotations.Nullable;

public record FitnessMapLatest(
        UUID fitnessTestId,
        LocalDate testedOn,
        /* 측정 항목 백분위 평균(1~99). 규준이 없는 항목만 있으면 null. */
        @Nullable Integer overallPercentile,
        /* 아래 셋은 부모만 볼 값이다. 호출 계정이 CHILD 면 null. */
        @Nullable FactorPoint weakest,
        @Nullable FactorPoint strongest,
        @Nullable CoachDirection coachDirection) {}
