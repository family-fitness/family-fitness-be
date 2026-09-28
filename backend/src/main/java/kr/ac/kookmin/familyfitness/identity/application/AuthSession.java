package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;

/**
 * 계정과 그 계정에 붙은 프로필들. `/me` 와 인증 응답이 공유하는 모양.
 *
 * @param selfProfileId 이 계정의 프로필. 한 계정 한 가족이라 profiles 는 0~1개이고 이 값은 그 하나다. 가족이 없으면 null
 */
public record AuthSession(
        UUID userId,
        NextStep nextStep,
        List<ProfileSummary> profiles,
        @Nullable UUID selfProfileId) {
    public static AuthSession of(UUID userId, NextStep nextStep, List<ProfileSummary> profiles) {
        return new AuthSession(
                userId,
                nextStep,
                profiles,
                profiles.isEmpty() ? null : profiles.getFirst().profileId());
    }
}
