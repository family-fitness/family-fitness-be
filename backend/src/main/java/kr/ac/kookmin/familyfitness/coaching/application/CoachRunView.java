package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import org.jspecify.annotations.Nullable;

/**
 * profileId · date — 누구의 어느 날을 짠 실행인지. 옛 주간 실행은 둘 다 null.
 * failureCode — FAILED 의 까닭 코드({@link CoachRunFailureCode} 이름). FAILED 가 아니면 null. 화면은 이 코드로 문구를 가른다.
 * notices — AI 가 제안과 함께 준 알림(또래 자료가 없어 다른 연령대 자료도 골랐다 등). 없으면 빈 목록.
 */
public record CoachRunView(
        UUID coachRunId,
        UUID familyId,
        CoachRunStatus status,
        LocalDate weekStart,
        @Nullable UUID profileId,
        @Nullable LocalDate date,
        @Nullable String summary,
        List<CoachStep> steps,
        @Nullable List<ProposalView> proposals,
        boolean canApprove,
        int missionCount,
        @Nullable String rejectedReason,
        @Nullable CoachRunFailureCode failureCode,
        List<String> notices) {}
