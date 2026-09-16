package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;

public record FavoriteCommand(UUID profileId, boolean favorited) {}
