package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 승인 대기 중인 코치 제안의 항목. 승인되면 이 값이 그대로 {@link Mission} 으로 복사된다. */
public record CoachProposalItem(
        int position,
        String title,
        String targetMetric,
        int targetValue,
        @Nullable String rationale,
        @Nullable String description,
        @Nullable LocalDate startsOn,
        @Nullable LocalDate endsOn,
        List<ProposalParticipant> participants,
        @Nullable ProposalVideo video,
        List<ProposalCitation> citations,
        @Nullable String copyChild,
        @Nullable String copyParent) {
    public CoachProposalItem(int position, String title, String targetMetric, int targetValue) {
        this(
                position,
                title,
                targetMetric,
                targetValue,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                null,
                null);
    }
}
