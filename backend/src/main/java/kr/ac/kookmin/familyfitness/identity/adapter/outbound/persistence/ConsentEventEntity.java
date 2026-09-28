package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * `consent_events` — 보호자 동의를 바꾼 한 번. 넣기만 하고 고치거나 지우지 않는다(V145).
 * id 는 DB 가 매긴다(V145 가 지난 동의를 옮겨 넣을 때 H2 · PostgreSQL 공통으로 id 를 만들 방법이 이것뿐이다).
 * V145 이전 철회를 옮긴 줄은 누가 · 무엇을 보냈는지 남아 있지 않아 actor · personalData · healthData 가 null 이다.
 */
@Entity
@Table(name = "consent_events")
public class ConsentEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private @Nullable Long id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "actor_user_id")
    private @Nullable UUID actorUserId;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "personal_data")
    private @Nullable Boolean personalData;

    @Column(name = "health_data")
    private @Nullable Boolean healthData;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ConsentEventEntity() {}

    public ConsentEventEntity(
            UUID profileId,
            @Nullable UUID actorUserId,
            String kind,
            @Nullable Boolean personalData,
            @Nullable Boolean healthData,
            Instant occurredAt) {
        this.profileId = profileId;
        this.actorUserId = actorUserId;
        this.kind = kind;
        this.personalData = personalData;
        this.healthData = healthData;
        this.occurredAt = occurredAt;
    }

    public @Nullable Long getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public @Nullable UUID getActorUserId() {
        return actorUserId;
    }

    public String getKind() {
        return kind;
    }

    public @Nullable Boolean getPersonalData() {
        return personalData;
    }

    public @Nullable Boolean getHealthData() {
        return healthData;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
