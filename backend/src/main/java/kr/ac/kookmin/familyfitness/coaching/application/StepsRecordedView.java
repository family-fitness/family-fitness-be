package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;

public record StepsRecordedView(
        ActivitySource source,
        boolean serverVerified,
        VerifiedBy verifiedBy,
        double missionProgress,
        boolean missionCompleted,
        boolean needsGuardianCheck) {}
