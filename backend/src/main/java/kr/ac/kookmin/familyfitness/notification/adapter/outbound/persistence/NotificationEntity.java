package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * `notifications` — unique(profile_id, dedupe_key). 넣은 뒤에는 read_at 만 바뀐다(조건부 UPDATE).
 * 키를 직접 정하는 엔티티라 {@link Persistable} 로 새 행임을 알린다 — 저장이 먼저 읽지(merge) 않고 곧바로 INSERT 한다.
 */
@Entity
@Table(name = "notifications")
public class NotificationEntity implements Persistable<UUID> {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", length = 200)
    private @Nullable String body;

    @Column(name = "about_profile_id")
    private @Nullable UUID aboutProfileId;

    @Column(name = "from_profile_id")
    private @Nullable UUID fromProfileId;

    @Column(name = "mission_id")
    private @Nullable UUID missionId;

    @Column(name = "cheer_id")
    private @Nullable UUID cheerId;

    @Column(name = "sticker_id", length = 20)
    private @Nullable String stickerId;

    @Column(name = "event_date")
    private @Nullable LocalDate eventDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private @Nullable Instant readAt;

    @Column(name = "dedupe_key", nullable = false, length = 100)
    private String dedupeKey;

    @Transient
    private boolean fresh = true;

    protected NotificationEntity() {}

    public NotificationEntity(
            UUID id,
            UUID profileId,
            String kind,
            String title,
            @Nullable String body,
            @Nullable UUID aboutProfileId,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            @Nullable UUID cheerId,
            @Nullable String stickerId,
            @Nullable LocalDate eventDate,
            Instant createdAt,
            @Nullable Instant readAt,
            String dedupeKey) {
        this.id = id;
        this.profileId = profileId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.aboutProfileId = aboutProfileId;
        this.fromProfileId = fromProfileId;
        this.missionId = missionId;
        this.cheerId = cheerId;
        this.stickerId = stickerId;
        this.eventDate = eventDate;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.dedupeKey = dedupeKey;
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
    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getBody() {
        return body;
    }

    public @Nullable UUID getAboutProfileId() {
        return aboutProfileId;
    }

    public @Nullable UUID getFromProfileId() {
        return fromProfileId;
    }

    public @Nullable UUID getMissionId() {
        return missionId;
    }

    public @Nullable UUID getCheerId() {
        return cheerId;
    }

    public @Nullable String getStickerId() {
        return stickerId;
    }

    public @Nullable LocalDate getEventDate() {
        return eventDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getReadAt() {
        return readAt;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}
