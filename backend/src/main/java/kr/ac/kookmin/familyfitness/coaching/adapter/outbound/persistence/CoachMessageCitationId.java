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
public class CoachMessageCitationId implements Serializable {
    @Column(name = "coach_message_id")
    private UUID coachMessageId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    private int position;

    protected CoachMessageCitationId() {}

    public CoachMessageCitationId(UUID coachMessageId, int position) {
        this.coachMessageId = coachMessageId;
        this.position = position;
    }

    public UUID getCoachMessageId() {
        return coachMessageId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof CoachMessageCitationId other)) return false;
        return position == other.position && Objects.equals(coachMessageId, other.coachMessageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coachMessageId, position);
    }
}
