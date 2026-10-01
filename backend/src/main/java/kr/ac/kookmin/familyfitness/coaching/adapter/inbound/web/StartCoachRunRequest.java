package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.StartCoachRunCommand;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidInputException;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 하루 편성 요청(FE 요청서 1장 ②, fe:src/lib/api/queries.ts PlanRequest).
 * minutesPerSession 은 minutes 가 없을 때만 쓴다(FE 가 옛 서버용으로 같이 보낸다). 옛 weekStart · daysPerWeek 는 받지 않는다(모르는 칸은 무시).
 * quiet · withParent 가 없으면 false, place 가 없으면 장소를 가리지 않는다. focusFactor 는 한글 요인 라벨(예: 유연성) 또는 null.
 * heightCm, weightKg 는 넣지 않아도 된다. 범위는 측정 등록(RegisterFitnessTestRequest)과 같고, 벗어나면 400 이다.
 */
public record StartCoachRunRequest(
        @NotNull @Nullable UUID profileId,
        @NotNull @Nullable LocalDate date,

        @Min(CoachRunConditions.MIN_MINUTES) @Max(CoachRunConditions.MAX_MINUTES) @Nullable
        Integer minutes,

        @Min(CoachRunConditions.MIN_MINUTES) @Max(CoachRunConditions.MAX_MINUTES) @Nullable
        Integer minutesPerSession,

        @Nullable Boolean quiet,
        @Nullable CoachPlace place,
        @Nullable FitnessFactor focusFactor,
        @Nullable Boolean withParent,

        @Schema(description = "대상의 키(cm), 30~230. 측정 기록이 없는 대상만 AI 요청에 싣는다(있으면 기록의 값). 벗어나면 400")
        @DecimalMin("30")
        @DecimalMax("230")
        @Nullable
        BigDecimal heightCm,

        @Schema(description = "대상의 몸무게(kg), 5~250. 쓰는 방식은 heightCm 과 같다. 벗어나면 400")
        @DecimalMin("5")
        @DecimalMax("250")
        @Nullable
        BigDecimal weightKg) {

    /** minutes 도 minutesPerSession 도 없으면 400(입력 오류라 {@link InvalidInputException}). 기본 분을 서버가 고르지 않는다. */
    StartCoachRunCommand toCommand() {
        Integer chosen = minutes != null ? minutes : minutesPerSession;
        if (chosen == null) throw new InvalidInputException("minutes: 운동할 분(5~60)이 필요합니다");
        return new StartCoachRunCommand(
                Objects.requireNonNull(profileId),
                Objects.requireNonNull(date),
                new CoachRunConditions(
                        chosen,
                        Boolean.TRUE.equals(quiet),
                        place,
                        focusFactor,
                        Boolean.TRUE.equals(withParent),
                        heightCm,
                        weightKg));
    }
}
