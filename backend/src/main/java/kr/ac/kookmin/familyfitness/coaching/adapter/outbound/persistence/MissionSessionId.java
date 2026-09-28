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
public class MissionSessionId implements Serializable {
    @Column(name = "mission_id")
    private UUID missionId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    private int position;

    protected MissionSessionId() {}

    public MissionSessionId(UUID missionId, int position) {
        this.missionId = missionId;
        this.position = position;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof MissionSessionId other)) return false;
        return position == other.position && Objects.equals(missionId, other.missionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, position);
    }
}
