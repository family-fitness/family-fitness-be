package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FavoriteRequest(
        @NotNull @Nullable UUID profileId,
        @NotNull @Nullable Boolean favorited) {}
