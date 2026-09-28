package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Embeddable
public class ExerciseFavoriteId implements Serializable {
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "clip_id", length = 48)
    private String clipId;

    protected ExerciseFavoriteId() {}

    public ExerciseFavoriteId(UUID profileId, String clipId) {
        this.profileId = profileId;
        this.clipId = clipId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getClipId() {
        return clipId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof ExerciseFavoriteId other)) return false;
        return Objects.equals(profileId, other.profileId) && Objects.equals(clipId, other.clipId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, clipId);
    }
}
