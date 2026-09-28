package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeel;
import org.jspecify.annotations.Nullable;

/** 운동이 어땠는지(FE 요청서 5장 {@code { profileId, feel: "EASY" | "GOOD" | "HARD" }}). */
public record MissionFeedbackRequest(
        @Schema(description = "느낌을 남기는 참여자 프로필") @NotNull @Nullable
        UUID profileId,

        @Schema(description = "EASY 쉬웠어요 · GOOD 딱 좋아요 · HARD 힘들었어요") @NotNull @Nullable
        MissionFeel feel) {}
