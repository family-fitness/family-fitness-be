package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 코치가 제안을 만든 한 번의 실행. 승인 전에는 미션이 아니다.
 * 한 실행은 한 사람(subjectProfileId)의 하루(runDate)를 조건(conditions)대로 짠다. 옛 주간 실행 행은 셋 다 null 이다.
 * 상태 전이와 승인 권한 규칙은 전부 여기에 있고, 애플리케이션은 트랜잭션과 포트 호출만 조합한다.
 */
public class CoachRun {
    public static final int MAX_REASON = 300;
    public static final int MAX_REFUSAL_REASON = 60;
    public static final int POLL_AFTER_MS = 1500;

    /** 같은 (프로필, 날짜)에 새 편성이 오면 기다리던 제안을 이 사유로 거절한다(결정 1). */
    public static final String SUPERSEDED_REASON = "새 제안으로 바뀌었어요";

    private final UUID id;
    private final UUID familyId;
    private final LocalDate weekStart;
    private final TriggerType triggerType;
    private final int daysPerWeek;
    private final int minutesPerSession;
    private final @Nullable UUID requestedBy;
    private final Instant createdAt;
    private final @Nullable UUID subjectProfileId;
    private final @Nullable LocalDate runDate;
    private final @Nullable CoachRunConditions conditions;

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

    /** FAILED 의 까닭(화면용 코드). FAILED 가 아니면 null. failureReason 은 같은 실패의 개발자용 원문이다. */
    private @Nullable CoachRunFailureCode failureCode;

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
            @Nullable UUID subjectProfileId,
            @Nullable LocalDate runDate,
            @Nullable CoachRunConditions conditions,
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
            @Nullable CoachRunFailureCode failureCode,
            Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.weekStart = weekStart;
        this.triggerType = triggerType;
        this.daysPerWeek = daysPerWeek;
        this.minutesPerSession = minutesPerSession;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
        this.subjectProfileId = subjectProfileId;
        this.runDate = runDate;
        this.conditions = conditions;
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
        this.failureCode = failureCode;
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

    /** 누구의 운동인지. 옛 주간 실행은 null. */
    public @Nullable UUID getSubjectProfileId() {
        return subjectProfileId;
    }

    /** 어느 날의 운동인지. 옛 주간 실행은 null. */
    public @Nullable LocalDate getRunDate() {
        return runDate;
    }

    public @Nullable CoachRunConditions getConditions() {
        return conditions;
    }

    /**
     * (프로필, 날짜) 잠금 키. RUNNING 동안만 값이 있고 끝나면 null 이다.
     * 저장소는 이 값에 유니크 인덱스를 걸어 같은 (프로필, 날짜)의 RUNNING 을 하나로 막는다(NULL 끼리는 겹치지 않는다).
     */
    public @Nullable String lockKey() {
        if (status != CoachRunStatus.RUNNING || subjectProfileId == null || runDate == null) return null;
        return lockKeyOf(subjectProfileId, runDate);
    }

    public static String lockKeyOf(UUID subjectProfileId, LocalDate runDate) {
        return subjectProfileId + "|" + runDate;
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

    public @Nullable CoachRunFailureCode getFailureCode() {
        return failureCode;
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

    /** 제안 항목이 미션이 될 때의 시작일. 항목에 기간이 없으면(옛 주간 실행) 실행의 주 월요일. */
    public LocalDate startsOnOf(CoachProposalItem item) {
        LocalDate startsOn = item.startsOn();
        return startsOn == null ? weekStart : startsOn;
    }

    /** 제안 항목이 미션이 될 때의 끝날. 항목에 기간이 없으면(옛 주간 실행) 실행의 주 일요일. */
    public LocalDate endsOnOf(CoachProposalItem item) {
        LocalDate endsOn = item.endsOn();
        return endsOn == null ? getWeekEnd() : endsOn;
    }

    /** 이 항목의 기간이 이미 지났다(끝날 &lt; 오늘 KST) — 승인해도 미션으로 만들지 않는다(결정 40 · 46). */
    public boolean isPastOn(CoachProposalItem item, LocalDate today) {
        return endsOnOf(item).isBefore(today);
    }

    /**
     * 승인해도 만들 미션이 하나도 남지 않을 만큼 기간이 지났다 — 참여자 있는 항목이 하나 이상이고 그 전부가 지났다.
     * 참여자 있는 항목이 없으면(만들 것이 원래 없다) 지난 것으로 보지 않는다. 승인은 409 PROPOSAL_EXPIRED, 조회의 canApprove 는 false.
     */
    public boolean isExpiredOn(LocalDate today) {
        List<CoachProposalItem> creatable =
                proposals.stream().filter(it -> !it.participants().isEmpty()).toList();
        return !creatable.isEmpty() && creatable.stream().allMatch(it -> isPastOn(it, today));
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

    public void fail(CoachRunFailureCode code, String reason, Instant at) {
        fail(code, reason, at, this.steps, false, null);
    }

    /**
     * AI 거부 · 대체 편성도 못 한 실패 · 예외. 까닭 코드와 개발자용 원문을 남기고 끝낸다.
     * 같은 (프로필, 날짜)로 새 실행을 다시 시작할 수 있다.
     */
    public void fail(
            CoachRunFailureCode code,
            String reason,
            Instant at,
            List<CoachStep> steps,
            boolean refused,
            @Nullable String refusalReason) {
        requireRunning();
        this.steps = steps;
        failureCode = code;
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

    /**
     * 요청 직후. AI 호출 전이며 커밋 후 비동기로 진행된다.
     * 한 사람의 하루 편성이다 — weekStart 는 그날이 든 주의 월요일, 주 횟수는 1, 회당 분은 conditions.minutes.
     */
    public static CoachRun start(
            UUID id,
            UUID familyId,
            UUID subjectProfileId,
            LocalDate runDate,
            CoachRunConditions conditions,
            @Nullable UUID requestedBy,
            Instant at) {
        return new CoachRun(
                id,
                familyId,
                runDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                TriggerType.MANUAL,
                1,
                conditions.minutes(),
                requestedBy,
                at,
                subjectProfileId,
                runDate,
                conditions,
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
                null,
                null,
                null,
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
            @Nullable UUID subjectProfileId,
            @Nullable LocalDate runDate,
            @Nullable CoachRunConditions conditions,
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
            @Nullable CoachRunFailureCode failureCode,
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
                subjectProfileId,
                runDate,
                conditions,
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
                failureCode,
                updatedAt);
    }
}
