package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ChatRequest(
        @NotNull @Nullable UUID profileId,
        @Nullable UUID conversationId,
        @NotBlank @Size(min = 1, max = 500) String question) {}
