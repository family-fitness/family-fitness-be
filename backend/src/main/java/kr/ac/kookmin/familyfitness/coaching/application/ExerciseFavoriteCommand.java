package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** @param profileId 누구의 찜인가. 비면 400 PROFILE_REQUIRED(FE 목과 같다). */
public record ExerciseFavoriteCommand(@Nullable UUID profileId, boolean favorited) {}
