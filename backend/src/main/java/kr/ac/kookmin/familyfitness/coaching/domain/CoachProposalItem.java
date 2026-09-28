package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 승인 대기 중인 코치 제안의 항목. 승인되면 이 값이 그대로 {@link Mission} 으로 복사된다(칸도 차례 그대로).
 *
 * @param sessions 준비 · 본 · 정리 칸, position 1..n 차례. 칸이 있으면 목표는 분(TIMER_MINUTES)이고 targetValue 가 칸 분의 합과 같다
 *     — 직접 만들기와 같은 규칙이다({@link Mission#manual}). 칸 없는 제안(옛 실행)은 빈 목록.
 */
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
        @Nullable String copyParent,
        List<MissionSession> sessions) {
    public CoachProposalItem {
        participants = List.copyOf(participants);
        citations = List.copyOf(citations);
        sessions = MissionSession.ordered(sessions);
        if (!sessions.isEmpty()) {
            if (!TargetMetric.TIMER_MINUTES.name().equals(targetMetric)) {
                throw new IllegalArgumentException("칸이 있는 제안의 목표 지표는 TIMER_MINUTES 여야 한다");
            }
            int total = MissionSession.totalMinutes(sessions);
            if (targetValue != total) {
                throw new IllegalArgumentException(
                        "칸이 있는 제안의 목표 분(targetValue " + targetValue + ")은 칸 시간의 합(" + total + "분)과 같아야 한다");
            }
        }
    }

    /** 칸 없는 제안. */
    public CoachProposalItem(
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
        this(
                position,
                title,
                targetMetric,
                targetValue,
                rationale,
                description,
                startsOn,
                endsOn,
                participants,
                video,
                citations,
                copyChild,
                copyParent,
                List.of());
    }

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
                null,
                List.of());
    }
}
