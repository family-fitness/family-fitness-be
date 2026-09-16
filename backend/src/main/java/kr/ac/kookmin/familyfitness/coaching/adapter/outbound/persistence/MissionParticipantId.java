package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Embeddable
public class MissionParticipantId implements Serializable {
    @Column(name = "mission_id")
    private UUID missionId;

    @Column(name = "profile_id")
    private UUID profileId;

    protected MissionParticipantId() {}

    public MissionParticipantId(UUID missionId, UUID profileId) {
        this.missionId = missionId;
        this.profileId = profileId;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof MissionParticipantId other)) return false;
        return Objects.equals(missionId, other.missionId) && Objects.equals(profileId, other.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, profileId);
    }
}
