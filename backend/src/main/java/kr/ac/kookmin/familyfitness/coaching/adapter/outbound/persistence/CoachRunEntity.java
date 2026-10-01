package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `coach_runs` 행. 도메인 {@link kr.ac.kookmin.familyfitness.coaching.domain.CoachRun} 과는 매퍼로만 오간다. */
@Entity
@Table(name = "coach_runs")
public class CoachRunEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "trigger_type", nullable = false, length = 10)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "summary")
    private @Nullable String summary;

    @Column(name = "steps_json")
    private @Nullable String stepsJson;

    @Column(name = "proposal_json")
    private @Nullable String proposalJson;

    @Column(name = "ai_run_id", length = 40)
    private @Nullable String aiRunId;

    @Column(name = "model_name", length = 40)
    private @Nullable String modelName;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "minutes_per_session", nullable = false)
    private int minutesPerSession;

    @Column(name = "requested_by_profile_id")
    private @Nullable UUID requestedByProfileId;

    @Column(name = "approved_by")
    private @Nullable UUID approvedBy;

    @Column(name = "approved_at")
    private @Nullable Instant approvedAt;

    @Column(name = "rejected_reason", length = 300)
    private @Nullable String rejectedReason;

    @Column(name = "rejected_at")
    private @Nullable Instant rejectedAt;

    @Column(name = "failure_reason", length = 300)
    private @Nullable String failureReason;

    @Column(name = "ai_refused", nullable = false)
    private boolean aiRefused;

    @Column(name = "ai_refusal_reason", length = 60)
    private @Nullable String aiRefusalReason;

    /** FAILED 의 까닭 코드(V139, CoachRunFailureCode 이름). FAILED 가 아니면 null. */
    @Column(name = "failure_code", length = 20)
    private @Nullable String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 하루 편성의 대상 · 날짜 · 조건(V134). 옛 주간 실행은 모두 null. 회당 분은 minutes_per_session 에 있다. */
    @Column(name = "subject_profile_id")
    private @Nullable UUID subjectProfileId;

    @Column(name = "run_date")
    private @Nullable LocalDate runDate;

    @Column(name = "quiet")
    private @Nullable Boolean quiet;

    @Column(name = "place", length = 10)
    private @Nullable String place;

    @Column(name = "focus_factor", length = 20)
    private @Nullable String focusFactor;

    @Column(name = "with_parent")
    private @Nullable Boolean withParent;

    /** 편성 요청에 실어 온 대상의 키(cm)와 몸무게(kg)(V171). 안 보냈거나 V171 앞에 만든 행이면 null 이다. */
    @Column(name = "height_cm", precision = 4, scale = 1)
    private @Nullable BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 4, scale = 1)
    private @Nullable BigDecimal weightKg;

    /** RUNNING 동안만 'profileId|runDate'. 유니크 인덱스 ux_coach_runs_lock_key 가 (프로필, 날짜) 잠금이다. */
    @Column(name = "lock_key", length = 60)
    private @Nullable String lockKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CoachRunEntity() {}

    public CoachRunEntity(
            UUID id,
            UUID familyId,
            LocalDate weekStart,
            String triggerType,
            String status,
            @Nullable String summary,
            @Nullable String stepsJson,
            @Nullable String proposalJson,
            @Nullable String aiRunId,
            @Nullable String modelName,
            int daysPerWeek,
            int minutesPerSession,
            @Nullable UUID requestedByProfileId,
            @Nullable UUID approvedBy,
            @Nullable Instant approvedAt,
            @Nullable String rejectedReason,
            @Nullable Instant rejectedAt,
            @Nullable String failureReason,
            boolean aiRefused,
            @Nullable String aiRefusalReason,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.weekStart = weekStart;
        this.triggerType = triggerType;
        this.status = status;
        this.summary = summary;
        this.stepsJson = stepsJson;
        this.proposalJson = proposalJson;
        this.aiRunId = aiRunId;
        this.modelName = modelName;
        this.daysPerWeek = daysPerWeek;
        this.minutesPerSession = minutesPerSession;
        this.requestedByProfileId = requestedByProfileId;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.rejectedReason = rejectedReason;
        this.rejectedAt = rejectedAt;
        this.failureReason = failureReason;
        this.aiRefused = aiRefused;
        this.aiRefusalReason = aiRefusalReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getTriggerType() {
        return triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public @Nullable String getSummary() {
        return summary;
    }

    public void setSummary(@Nullable String summary) {
        this.summary = summary;
    }

    public @Nullable String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(@Nullable String stepsJson) {
        this.stepsJson = stepsJson;
    }

    public @Nullable String getProposalJson() {
        return proposalJson;
    }

    public void setProposalJson(@Nullable String proposalJson) {
        this.proposalJson = proposalJson;
    }

    public @Nullable String getAiRunId() {
        return aiRunId;
    }

    public void setAiRunId(@Nullable String aiRunId) {
        this.aiRunId = aiRunId;
    }

    public @Nullable String getModelName() {
        return modelName;
    }

    public void setModelName(@Nullable String modelName) {
        this.modelName = modelName;
    }

    public int getDaysPerWeek() {
        return daysPerWeek;
    }

    public int getMinutesPerSession() {
        return minutesPerSession;
    }

    public @Nullable UUID getRequestedByProfileId() {
        return requestedByProfileId;
    }

    public @Nullable UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(@Nullable UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public @Nullable Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(@Nullable Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public @Nullable String getRejectedReason() {
        return rejectedReason;
    }

    public void setRejectedReason(@Nullable String rejectedReason) {
        this.rejectedReason = rejectedReason;
    }

    public @Nullable Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(@Nullable Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public @Nullable String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(@Nullable String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isAiRefused() {
        return aiRefused;
    }

    public void setAiRefused(boolean aiRefused) {
        this.aiRefused = aiRefused;
    }

    public @Nullable String getAiRefusalReason() {
        return aiRefusalReason;
    }

    public void setAiRefusalReason(@Nullable String aiRefusalReason) {
        this.aiRefusalReason = aiRefusalReason;
    }

    public @Nullable String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(@Nullable String failureCode) {
        this.failureCode = failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public @Nullable UUID getSubjectProfileId() {
        return subjectProfileId;
    }

    public @Nullable LocalDate getRunDate() {
        return runDate;
    }

    public @Nullable Boolean getQuiet() {
        return quiet;
    }

    public @Nullable String getPlace() {
        return place;
    }

    public @Nullable String getFocusFactor() {
        return focusFactor;
    }

    public @Nullable Boolean getWithParent() {
        return withParent;
    }

    public @Nullable BigDecimal getHeightCm() {
        return heightCm;
    }

    public @Nullable BigDecimal getWeightKg() {
        return weightKg;
    }

    /** 편성 요청(대상 · 날짜 · 조건). 행을 처음 만들 때만 쓴다. */
    public void setRequest(
            @Nullable UUID subjectProfileId,
            @Nullable LocalDate runDate,
            @Nullable Boolean quiet,
            @Nullable String place,
            @Nullable String focusFactor,
            @Nullable Boolean withParent,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg) {
        this.subjectProfileId = subjectProfileId;
        this.runDate = runDate;
        this.quiet = quiet;
        this.place = place;
        this.focusFactor = focusFactor;
        this.withParent = withParent;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
    }

    public @Nullable String getLockKey() {
        return lockKey;
    }

    public void setLockKey(@Nullable String lockKey) {
        this.lockKey = lockKey;
    }
}
