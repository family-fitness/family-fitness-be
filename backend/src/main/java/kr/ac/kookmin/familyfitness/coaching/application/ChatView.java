package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ChatView(
        UUID conversationId,
        UUID messageId,
        String answer,
        List<ChatCitationView> citations,
        boolean refused,
        @Nullable String refusalReason) {}
