package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PredictCommand(
        /** 생략하면 최신 회차 */
        @Nullable UUID fitnessTestId, int horizonYears, String itemCode) {
    public static final int DEFAULT_HORIZON_YEARS = 10;
    public static final String DEFAULT_ITEM_CODE = "028";

    public PredictCommand() {
        this(null, DEFAULT_HORIZON_YEARS, DEFAULT_ITEM_CODE);
    }
}
