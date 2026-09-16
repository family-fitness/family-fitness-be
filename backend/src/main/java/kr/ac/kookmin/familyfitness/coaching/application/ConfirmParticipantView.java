package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import org.jspecify.annotations.Nullable;

public record ConfirmParticipantView(
        UUID missionId,
        UUID profileId,
        boolean completed,
        @Nullable VerifiedBy verifiedBy,
        @Nullable UUID confirmedBy,
        @Nullable Instant verifiedAt) {}
