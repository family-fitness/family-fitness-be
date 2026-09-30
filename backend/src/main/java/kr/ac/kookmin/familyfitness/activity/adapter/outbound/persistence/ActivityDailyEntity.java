package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * `activity_daily` — unique(profile_id, activity_date, source). 시간은 {@code active_seconds} 가 기준이고(V144),
 * {@code active_minutes} 는 호환용으로 그 초를 내림한 분을 같이 적는다.
 */
@Entity
@Table(name = "activity_daily")
public class ActivityDailyEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "steps", nullable = false)
    private int steps;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "active_minutes", nullable = false)
    private int activeMinutes;

    @Column(name = "active_seconds", nullable = false)
    private int activeSeconds;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ActivityDailyEntity() {}

    public ActivityDailyEntity(
            UUID id,
            UUID profileId,
            LocalDate activityDate,
            String source,
            int steps,
            int activeSeconds,
            Instant recordedAt) {
        this.id = id;
        this.profileId = profileId;
        this.activityDate = activityDate;
        this.source = source;
        this.steps = steps;
        this.activeSeconds = activeSeconds;
        this.activeMinutes = activeSeconds / 60;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public String getSource() {
        return source;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public int getActiveSeconds() {
        return activeSeconds;
    }

    /** 초를 쓰면 호환용 분(내림)도 같이 맞춘다. */
    public void setActiveSeconds(int activeSeconds) {
        this.activeSeconds = activeSeconds;
        this.activeMinutes = activeSeconds / 60;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
