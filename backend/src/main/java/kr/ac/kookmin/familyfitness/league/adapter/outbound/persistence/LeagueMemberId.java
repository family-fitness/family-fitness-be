package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Embeddable
public class LeagueMemberId implements Serializable {
    @Column(name = "round_id")
    private UUID roundId;

    @Column(name = "family_id")
    private UUID familyId;

    protected LeagueMemberId() {}

    public LeagueMemberId(UUID roundId, UUID familyId) {
        this.roundId = roundId;
        this.familyId = familyId;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof LeagueMemberId other)) return false;
        return Objects.equals(roundId, other.roundId) && Objects.equals(familyId, other.familyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundId, familyId);
    }
}
