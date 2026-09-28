package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import org.jspecify.annotations.Nullable;

/**
 * 날짜는 둘 중 하나로 보낸다 — {@code startDate} · {@code endDate}(미션 한 건) 또는 {@code dates}(날마다 하루짜리 한 건씩).
 * 둘 다 보내거나 둘 다 없으면 400 이다. 어느 날이든 오늘(KST)보다 앞이면 422 INVALID_DATE 이고 아무것도 만들지 않는다.
 *
 * @param targetValue 칸({@code sessions})이 있으면 칸 {@code minutes} 의 합과 같아야 한다. 다르면 400 이다
 * @param sessions 칸. 최대 10개(직접 짜기 화면의 상한). 없거나 비면 칸 없는 미션이다
 */
public record CreateMissionRequest(
        @Schema(
                description = "운동 이름. 1~" + TITLE_MAX + "자. 넘으면 400 BAD_REQUEST(서버가 잘라 저장하지 않는다)",
                minLength = 1,
                maxLength = TITLE_MAX)
        @NotBlank
        @Size(min = 1, max = TITLE_MAX)
        String title,

        @Schema(description = "시작일(KST). dates 를 보내면 비운다. 오늘보다 앞이면 422 INVALID_DATE") @Nullable
        LocalDate startDate,

        @Schema(description = "끝날(KST, 양끝 포함). startDate 와 같이 보낸다. startDate 보다 앞이면 400") @Nullable
        LocalDate endDate,

        @NotNull @Nullable TargetMetric targetMetric,
        @NotNull @Min(1) @Nullable Integer targetValue,
        @Nullable String videoId,
        @NotEmpty @Size(min = 1, max = 5) List<UUID> participantProfileIds,
        @Size(max = 10) @Nullable List<@NotNull @Valid MissionSessionRequest> sessions,

        @Schema(
                description = "여러 날 한 번에(최대 " + DATES_MAX + "일 — 7요일 × 4주). 날마다 하루짜리 미션을 같은 이름 · 칸 · 참여자로"
                        + " 만들고, 같은 날은 한 번만 만든다. 하나라도 틀리면 아무것도 만들지 않는다. startDate · endDate 와 같이 보내지 않는다")
        @Size(min = 1, max = DATES_MAX)
        @Nullable
        List<@NotNull LocalDate> dates) {

    /** 제목 상한. 컬럼은 120자지만 직접 만들기는 지금 값(50자)을 그대로 둔다(결정 40 · MS-15). */
    public static final int TITLE_MAX = 50;

    /** 여러 날 만들기 상한 — 직접 짜기 화면이 고를 수 있는 가장 많은 날(fe:src/lib/routine.ts 7요일 × 4주). */
    public static final int DATES_MAX = 28;
}
