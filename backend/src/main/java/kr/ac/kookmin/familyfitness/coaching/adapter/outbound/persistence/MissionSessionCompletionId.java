package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Embeddable
public class MissionSessionCompletionId implements Serializable {
    @Column(name = "mission_id")
    private UUID missionId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    private int position;

    @Column(name = "profile_id")
    private UUID profileId;

    protected MissionSessionCompletionId() {}

    public MissionSessionCompletionId(UUID missionId, int position, UUID profileId) {
        this.missionId = missionId;
        this.position = position;
        this.profileId = profileId;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getProfileId() {
        return profileId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof MissionSessionCompletionId other)) return false;
        return position == other.position
                && Objects.equals(missionId, other.missionId)
                && Objects.equals(profileId, other.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, position, profileId);
    }
}
