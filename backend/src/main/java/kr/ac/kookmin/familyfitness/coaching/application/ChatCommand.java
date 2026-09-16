package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ChatCommand(UUID profileId, @Nullable UUID conversationId, String question) {}
