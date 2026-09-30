package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.AuthSession;
import kr.ac.kookmin.familyfitness.identity.application.NextStep;
import org.jspecify.annotations.Nullable;

/** selfProfileId 는 이 계정의 프로필(없으면 null). profiles 는 이 계정에 붙은 프로필이라 0~1개다. */
public record MeResponse(
        UUID userId,
        NextStep nextStep,
        List<ProfileSummary> profiles,
        @Nullable UUID selfProfileId) {
    public static MeResponse of(AuthSession session) {
        return new MeResponse(session.userId(), session.nextStep(), session.profiles(), session.selfProfileId());
    }
}
