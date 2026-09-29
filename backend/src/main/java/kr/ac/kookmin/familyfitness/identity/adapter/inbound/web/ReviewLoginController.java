package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.servlet.http.HttpServletRequest;
import kr.ac.kookmin.familyfitness.identity.application.ReviewLoginService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 심사용 계정 로그인. `app.auth.review-login.enabled=true`(local · compose · prod) 일 때만 빈으로 등록되고, 꺼져 있으면 경로가
 * 없어 404 다. 본문 없이 부르면 새 계정과 체험 가족을 만들고 dev-login 과 같은 모양(AuthResponse, nextStep HOME)을 준다.
 *
 * <p>dev-login 과 달리 개발용 기능이 아니다. 심사위원이 운영 서버에서 구글 계정 없이 들어오는 길이라 운영에서 켜 두고,
 * DevFeatureGuard 목록에 넣지 않는다. 대신 남의 계정이 될 수는 없고(새 계정, 또는 새 계정 한도가 찼을 때 최근에 만든 심사용 계정) IP 마다 한 시간에
 * 30번까지다. 새 계정은 모두 합쳐 한 시간에 300개까지 만든다(ReviewLoginLimiter).
 */
@RestController
@ConditionalOnProperty(name = "app.auth.review-login.enabled", havingValue = "true")
public class ReviewLoginController {
    private final ReviewLoginService reviews;

    public ReviewLoginController(ReviewLoginService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/api/v1/auth/review-login")
    public AuthResponse reviewLogin(HttpServletRequest request) {
        return AuthResponse.of(reviews.login(request.getRemoteAddr()));
    }
}
