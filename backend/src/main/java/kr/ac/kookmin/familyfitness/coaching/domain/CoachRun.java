package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주간 코치가 제안을 만든 한 번의 실행. 승인 전에는 미션이 아니다.
 * 상태 전이와 승인 권한 규칙은 전부 여기에 있고, 애플리케이션은 트랜잭션과 포트 호출만 조합한다.
 */
public class CoachRun {
    public static final int MAX_REASON = 300;
    public static final int MAX_REFUSAL_REASON = 60;
    public static final int POLL_AFTER_MS = 1500;

    private final UUID id;
    private final UUID familyId;
    private final LocalDate weekStart;
    private final TriggerType triggerType;
    private final int daysPerWeek;
    private final int minutesPerSession;
    private final @Nullable UUID requestedBy;
    private final Instant createdAt;

    private CoachRunStatus status;
    private List<CoachProposalItem> proposals;
    private List<CoachStep> steps;
    private @Nullable String summary;

    /** AI 가 돌려준 proposal 원문. 감사·재변환용이며 화면에는 나가지 않는다. */
    private @Nullable String proposalJson;

    private @Nullable String aiRunId;
    private @Nullable String modelName;
    private @Nullable UUID approvedBy;
    private @Nullable Instant approvedAt;
    private @Nullable String rejectedReason;
    private @Nullable Instant rejectedAt;
    private @Nullable String failureReason;
    private boolean aiRefused;
    private @Nullable String aiRefusalReason;
    private Instant updatedAt;

    private CoachRun(
            UUID id,
            UUID familyId,
            LocalDate weekStart,
            TriggerType triggerType,
            int daysPerWeek,
            int minutesPerSession,
            @Nullable UUID requestedBy,
            Instant createdAt,
            CoachRunStatus status,
            List<CoachProposalItem> proposals,
            List<CoachStep> steps,
            @Nullable String summary,
            @Nullable String proposalJson,
            @Nullable String aiRunId,
            @Nullable String modelName,
            @Nullable UUID approvedBy,
            @Nullable Instant approvedAt,
            @Nullable String rejectedReason,
            @Nullable Instant rejectedAt,
            @Nullable String failureReason,
            boolean aiRefused,
            @Nullable String aiRefusalReason,
            Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.weekStart = weekStart;
        this.triggerType = triggerType;
        this.daysPerWeek = daysPerWeek;
        this.minutesPerSession = minutesPerSession;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
        this.status = status;
        this.proposals = sortedByPosition(proposals);
        this.steps = steps;
        this.summary = summary;
        this.proposalJson = proposalJson;
        this.aiRunId = aiRunId;
        this.modelName = modelName;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.rejectedReason = rejectedReason;
        this.rejectedAt = rejectedAt;
        this.failureReason = failureReason;
        this.aiRefused = aiRefused;
        this.aiRefusalReason = aiRefusalReason;
        this.updatedAt = updatedAt;
    }

    private static List<CoachProposalItem> sortedByPosition(List<CoachProposalItem> items) {
        return items.stream()
                .sorted(Comparator.comparingInt(CoachProposalItem::position))
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public int getDaysPerWeek() {
        return daysPerWeek;
    }

    public int getMinutesPerSession() {
        return minutesPerSession;
    }

    public @Nullable UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CoachRunStatus getStatus() {
        return status;
    }

    public List<CoachProposalItem> getProposals() {
        return proposals;
    }

    public List<CoachStep> getSteps() {
        return steps;
    }

    public @Nullable String getSummary() {
        return summary;
    }

    public @Nullable String getProposalJson() {
        return proposalJson;
    }

    public @Nullable String getAiRunId() {
        return aiRunId;
    }

    public @Nullable String getModelName() {
        return modelName;
    }

    public @Nullable UUID getApprovedBy() {
        return approvedBy;
    }

    public @Nullable Instant getApprovedAt() {
        return approvedAt;
    }

    public @Nullable String getRejectedReason() {
        return rejectedReason;
    }

    public @Nullable Instant getRejectedAt() {
        return rejectedAt;
    }

    public @Nullable String getFailureReason() {
        return failureReason;
    }

    public boolean isAiRefused() {
        return aiRefused;
    }

    public @Nullable String getAiRefusalReason() {
        return aiRefusalReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LocalDate getWeekEnd() {
        return weekStart.plusDays(6);
    }

    public boolean isAwaitingApproval() {
        return status == CoachRunStatus.AWAITING_APPROVAL;
    }

    /** 같은 가족의 부모만, 승인 대기 상태에서만. 실패해도 상태는 그대로다. */
    public void approve(CoachApprover approver, Instant at) {
        authorize(approver);
        requireAwaitingApproval();
        status = CoachRunStatus.APPROVED;
        approvedBy = approver.profileId();
        approvedAt = at;
        updatedAt = at;
    }

    public void reject(CoachApprover approver, @Nullable String reason) {
        reject(approver, reason, null);
    }

    public void reject(CoachApprover approver, @Nullable String reason, @Nullable Instant at) {
        authorize(approver);
        requireAwaitingApproval();
        status = CoachRunStatus.REJECTED;
        rejectedReason = reason == null ? null : take(reason, MAX_REASON);
        rejectedAt = at;
        if (at != null) updatedAt = at;
    }

    /** 승인된 실행의 제안만 미션 생성에 쓸 수 있다. */
    public List<CoachProposalItem> proposalsForMissionCreation() {
        if (status != CoachRunStatus.APPROVED) throw new CoachApprovalRequiredException();
        return proposals;
    }

    /** AI 가 run 을 받았다. 폴링 식별자를 기억해 둔다. */
    public void attachAiRun(String aiRunId, Instant at) {
        requireRunning();
        this.aiRunId = aiRunId;
        updatedAt = at;
    }

    /** AI 가 `succeeded` 를 돌려줬다. 변환된 제안을 붙이고 승인 대기로 옮긴다. */
    public void complete(
            List<CoachStep> steps,
            List<CoachProposalItem> proposals,
            @Nullable String proposalJson,
            @Nullable String summary,
            @Nullable String modelName,
            Instant at) {
        requireRunning();
        this.steps = steps;
        this.proposals = sortedByPosition(proposals);
        this.proposalJson = proposalJson;
        this.summary = summary;
        this.modelName = modelName;
        status = CoachRunStatus.AWAITING_APPROVAL;
        updatedAt = at;
    }

    public void fail(String reason, Instant at) {
        fail(reason, at, this.steps, false, null);
    }

    /** AI 거부·실패·타임아웃·예외. 사유만 남기고 끝낸다. 같은 주에 새 실행을 다시 시작할 수 있다. */
    public void fail(
            String reason, Instant at, List<CoachStep> steps, boolean refused, @Nullable String refusalReason) {
        requireRunning();
        this.steps = steps;
        failureReason = take(reason, MAX_REASON);
        aiRefused = refused;
        aiRefusalReason = refusalReason == null ? null : take(refusalReason, MAX_REFUSAL_REASON);
        status = CoachRunStatus.FAILED;
        updatedAt = at;
    }

    private void authorize(CoachApprover approver) {
        if (!approver.isParent()) throw new ParentRoleRequiredException();
        if (!approver.familyId().equals(familyId)) throw new CoachFamilyAccessDeniedException();
    }

    private void requireAwaitingApproval() {
        if (status != CoachRunStatus.AWAITING_APPROVAL) throw new CoachRunAlreadyDecidedException(status);
    }

    private void requireRunning() {
        if (status != CoachRunStatus.RUNNING) throw new CoachRunAlreadyDecidedException(status);
    }

    private static String take(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }

    /** 요청 직후. AI 호출 전이며 커밋 후 비동기로 진행된다. */
    public static CoachRun start(
            UUID id,
            UUID familyId,
            LocalDate weekStart,
            TriggerType triggerType,
            int daysPerWeek,
            int minutesPerSession,
            @Nullable UUID requestedBy,
            Instant at) {
        return new CoachRun(
                id,
                familyId,
                weekStart,
                triggerType,
                daysPerWeek,
                minutesPerSession,
                requestedBy,
                at,
                CoachRunStatus.RUNNING,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                at);
    }

    /** 제안이 준비된 승인 대기 실행. 도메인 테스트와 변환 결과 조립에 쓴다. */
    public static CoachRun awaitingApproval(UUID id, UUID familyId, List<CoachProposalItem> proposals) {
        return awaitingApproval(id, familyId, proposals, LocalDate.EPOCH, List.of(), null, 3, 15, Instant.EPOCH);
    }

    public static CoachRun awaitingApproval(
            UUID id,
            UUID familyId,
            List<CoachProposalItem> proposals,
            LocalDate weekStart,
            List<CoachStep> steps,
            @Nullable String summary,
            int daysPerWeek,
            int minutesPerSession,
            Instant at) {
        return new CoachRun(
                id,
                familyId,
                weekStart,
                TriggerType.MANUAL,
                daysPerWeek,
                minutesPerSession,
                null,
                at,
                CoachRunStatus.AWAITING_APPROVAL,
                proposals,
                steps,
                summary,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                at);
    }

    /** 저장소에서 복원. 규칙 검사 없이 상태를 그대로 싣는다. */
    public static CoachRun reconstitute(
            UUID id,
            UUID familyId,
            LocalDate weekStart,
            TriggerType triggerType,
            int daysPerWeek,
            int minutesPerSession,
            @Nullable UUID requestedBy,
            Instant createdAt,
            CoachRunStatus status,
            List<CoachProposalItem> proposals,
            List<CoachStep> steps,
            @Nullable String summary,
            @Nullable String proposalJson,
            @Nullable String aiRunId,
            @Nullable String modelName,
            @Nullable UUID approvedBy,
            @Nullable Instant approvedAt,
            @Nullable String rejectedReason,
            @Nullable Instant rejectedAt,
            @Nullable String failureReason,
            boolean aiRefused,
            @Nullable String aiRefusalReason,
            Instant updatedAt) {
        return new CoachRun(
                id,
                familyId,
                weekStart,
                triggerType,
                daysPerWeek,
                minutesPerSession,
                requestedBy,
                createdAt,
                status,
                proposals,
                steps,
                summary,
                proposalJson,
                aiRunId,
                modelName,
                approvedBy,
                approvedAt,
                rejectedReason,
                rejectedAt,
                failureReason,
                aiRefused,
                aiRefusalReason,
                updatedAt);
    }
}
