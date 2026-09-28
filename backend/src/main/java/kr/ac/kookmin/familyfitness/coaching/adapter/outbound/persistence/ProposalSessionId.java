package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** 제안 칸의 키. (실행, 제안 항목 position, 칸 position). */
@Embeddable
public class ProposalSessionId implements Serializable {
    @Column(name = "coach_run_id")
    private UUID coachRunId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "item_position")
    private int itemPosition;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "position")
    private int position;

    protected ProposalSessionId() {}

    public ProposalSessionId(UUID coachRunId, int itemPosition, int position) {
        this.coachRunId = coachRunId;
        this.itemPosition = itemPosition;
        this.position = position;
    }

    public UUID getCoachRunId() {
        return coachRunId;
    }

    public int getItemPosition() {
        return itemPosition;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof ProposalSessionId other)) return false;
        return itemPosition == other.itemPosition
                && position == other.position
                && Objects.equals(coachRunId, other.coachRunId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coachRunId, itemPosition, position);
    }
}
