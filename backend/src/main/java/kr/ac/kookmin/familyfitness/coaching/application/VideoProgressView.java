package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import org.jspecify.annotations.Nullable;

public record VideoProgressView(
        double maxProgress,
        boolean completed,
        int creditedMinutes,
        @Nullable VerifiedBy verifiedBy,
        @Nullable Double missionProgress) {}
