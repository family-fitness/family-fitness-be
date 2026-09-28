package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.StartCoachRunCommand;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 하루 편성 요청(FE 요청서 1장 ②, fe:src/lib/api/queries.ts PlanRequest).
 * minutesPerSession 은 minutes 가 없을 때만 쓴다(FE 가 옛 서버용으로 같이 보낸다). 옛 weekStart · daysPerWeek 는 받지 않는다(모르는 칸은 무시).
 * quiet · withParent 가 없으면 false, place 가 없으면 장소를 가리지 않는다. focusFactor 는 한글 요인 라벨(예: 유연성) 또는 null.
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
        @Nullable Boolean withParent) {

    /** minutes 도 minutesPerSession 도 없으면 400(IllegalArgumentException). 기본 분을 서버가 고르지 않는다. */
    StartCoachRunCommand toCommand() {
        Integer chosen = minutes != null ? minutes : minutesPerSession;
        if (chosen == null) throw new IllegalArgumentException("minutes: 운동할 분(5~60)이 필요합니다");
        return new StartCoachRunCommand(
                Objects.requireNonNull(profileId),
                Objects.requireNonNull(date),
                new CoachRunConditions(
                        chosen, Boolean.TRUE.equals(quiet), place, focusFactor, Boolean.TRUE.equals(withParent)));
    }
}
