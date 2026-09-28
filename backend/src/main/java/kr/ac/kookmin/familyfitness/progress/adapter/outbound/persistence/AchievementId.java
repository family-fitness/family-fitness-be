package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Embeddable
public class AchievementId implements Serializable {
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "code", length = 20)
    private String code;

    protected AchievementId() {}

    public AchievementId(UUID profileId, String code) {
        this.profileId = profileId;
        this.code = code;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof AchievementId other)) return false;
        return Objects.equals(profileId, other.profileId) && Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, code);
    }
}
