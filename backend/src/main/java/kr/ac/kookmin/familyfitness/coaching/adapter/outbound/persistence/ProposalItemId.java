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
public class ProposalItemId implements Serializable {
    @Column(name = "coach_run_id")
    private UUID coachRunId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    private int position;

    protected ProposalItemId() {}

    public ProposalItemId(UUID coachRunId, int position) {
        this.coachRunId = coachRunId;
        this.position = position;
    }

    public UUID getCoachRunId() {
        return coachRunId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof ProposalItemId other)) return false;
        return position == other.position && Objects.equals(coachRunId, other.coachRunId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coachRunId, position);
    }
}
