package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ProposalView(
        int position,
        String title,
        @Nullable String rationale,
        String targetMetric,
        int targetValue,
        LocalDate startDate,
        LocalDate endDate,
        List<ProposalParticipantView> participants,
        @Nullable ProposalVideoView video,
        List<ProposalCitationView> citations) {}
