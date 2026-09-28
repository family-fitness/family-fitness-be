package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 운동할 수 있는 시간 한 칸의 키. (프로필, 요일) — 하루 한 칸. */
@Embeddable
public class AvailabilitySlotId implements Serializable {
    @Column(name = "profile_id")
    private UUID profileId;

    /** MON … SUN */
    @Column(name = "day_of_week", length = 3)
    private String dayOfWeek;

    protected AvailabilitySlotId() {}

    public AvailabilitySlotId(UUID profileId, String dayOfWeek) {
        this.profileId = profileId;
        this.dayOfWeek = dayOfWeek;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof AvailabilitySlotId other)) return false;
        return Objects.equals(profileId, other.profileId) && Objects.equals(dayOfWeek, other.dayOfWeek);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, dayOfWeek);
    }
}
