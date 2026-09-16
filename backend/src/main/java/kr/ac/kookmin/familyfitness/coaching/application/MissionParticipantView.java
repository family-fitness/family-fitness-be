package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import org.jspecify.annotations.Nullable;

public record MissionParticipantView(
        UUID profileId,
        @Nullable String name,
        double progress,
        boolean completed,
        @Nullable VerifiedBy verifiedBy,
        boolean needsGuardianCheck) {}
