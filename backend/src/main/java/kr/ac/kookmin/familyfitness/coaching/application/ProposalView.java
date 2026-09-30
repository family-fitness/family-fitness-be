package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @param sessions 준비 · 본 · 정리 칸, position 차례. 모양은 미션의 칸({@link MissionView#sessions})과 같다 — 승인하면 그대로 복사된다.
 *     칸 없는 제안(옛 실행)은 빈 목록
 */
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
        List<ProposalCitationView> citations,
        List<MissionSessionView> sessions) {}
