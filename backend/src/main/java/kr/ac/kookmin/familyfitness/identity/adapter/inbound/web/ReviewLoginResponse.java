package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.NextStep;
import kr.ac.kookmin.familyfitness.identity.application.ReviewLoginResult;
import org.jspecify.annotations.Nullable;

/**
 * 심사용 계정 로그인 응답. {@link AuthResponse} 와 같은 필드에 inviteCode 하나를 더 싣는다(api-contract §1).
 *
 * @param inviteCode INVITED 로 들어와 아직 합류하지 않았으면 체험 가족 아빠 자리의 초대코드(nextStep CLAIM). FAMILY · FRESH 는 null
 */
public record ReviewLoginResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        NextStep nextStep,
        List<ProfileSummary> profiles,
        @Nullable UUID selfProfileId,
        @Nullable String inviteCode) {
    public static ReviewLoginResponse of(ReviewLoginResult result) {
        AuthResponse auth = AuthResponse.of(result.auth());
        return new ReviewLoginResponse(
                auth.accessToken(),
                auth.refreshToken(),
                auth.userId(),
                auth.nextStep(),
                auth.profiles(),
                auth.selfProfileId(),
                result.inviteCode());
    }
}
