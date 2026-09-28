package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.AuthResult;
import kr.ac.kookmin.familyfitness.identity.application.NextStep;
import org.jspecify.annotations.Nullable;

// 응답 본문. 계약(api-contract §1)의 필드 이름을 그대로 쓴다. ProfileSummary 는 공개 언어 그대로 내보낸다.
// userId · nextStep · profiles · selfProfileId 는 /me 와 같은 값이다.
public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        NextStep nextStep,
        List<ProfileSummary> profiles,
        @Nullable UUID selfProfileId) {
    public static AuthResponse of(AuthResult result) {
        return new AuthResponse(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                result.session().userId(),
                result.session().nextStep(),
                result.session().profiles(),
                result.session().selfProfileId());
    }
}
