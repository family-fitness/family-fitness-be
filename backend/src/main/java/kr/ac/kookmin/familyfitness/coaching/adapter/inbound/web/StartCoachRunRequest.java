package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record StartCoachRunRequest(
        @Nullable LocalDate weekStart,
        @Min(1) @Max(7) Integer daysPerWeek,
        @Min(5) @Max(60) Integer minutesPerSession) {
    public StartCoachRunRequest {
        daysPerWeek = daysPerWeek == null ? 3 : daysPerWeek;
        minutesPerSession = minutesPerSession == null ? 15 : minutesPerSession;
    }

    public StartCoachRunRequest() {
        this(null, 3, 15);
    }
}
