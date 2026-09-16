package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/** 편성 역할: CHILD → 주행자 · PARENT(WEEKEND·FULL) → 동반자 · PARENT(CHEER_ONLY·null) → 응원. */
public final class CoachRoles {
    public static final String DRIVER = "주행자";
    public static final String COMPANION = "동반자";
    public static final String CHEER = "응원";

    private CoachRoles() {}

    public static String of(ProfileRole role, @Nullable SupportMode supportMode) {
        if (role == ProfileRole.CHILD) return DRIVER;
        if (supportMode == null) return CHEER;
        if (supportMode == SupportMode.CHEER_ONLY) return CHEER;
        return COMPANION;
    }
}
