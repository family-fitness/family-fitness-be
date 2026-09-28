package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * `profile_availability_slots` 한 행. 한 주를 바꿀 때 지우고 다시 넣을 뿐 고치지 않는다(수정자 없음).
 * 키를 직접 정하는 엔티티라 {@link Persistable} 로 새 행임을 알린다 — 저장 때 행마다 있는지 먼저 읽지(merge) 않는다.
 */
@Entity
@Table(name = "profile_availability_slots")
public class AvailabilitySlotEntity implements Persistable<AvailabilitySlotId> {
    @EmbeddedId
    private AvailabilitySlotId id;

    /** 한국 시각의 시:분 */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "minutes", nullable = false)
    private int minutes;

    /** 저장한 보호자 프로필 */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean fresh = true;

    protected AvailabilitySlotEntity() {}

    public AvailabilitySlotEntity(
            AvailabilitySlotId id, LocalTime startTime, int minutes, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.startTime = startTime;
        this.minutes = minutes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @Override
    public AvailabilitySlotId getId() {
        return id;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getMinutes() {
        return minutes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
