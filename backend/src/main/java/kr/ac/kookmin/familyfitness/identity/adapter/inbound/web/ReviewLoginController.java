package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.servlet.http.HttpServletRequest;
import kr.ac.kookmin.familyfitness.identity.application.ReviewLoginKind;
import kr.ac.kookmin.familyfitness.identity.application.ReviewLoginService;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 심사용 계정 로그인. `app.auth.review-login.enabled=true`(local · compose · prod) 일 때만 빈으로 등록되고, 꺼져 있으면 경로가
 * 없어 404 다. 본문 {@code {"kind": "FAMILY" | "FRESH" | "INVITED"}} 로 kind 세 가지 가운데 하나를 고른다({@link ReviewLoginKind}).
 * 본문이 없거나 kind 가 없으면 FAMILY — 새 계정과 체험 가족을 만들고 nextStep HOME 이다. 모르는 kind 는 400 BAD_REQUEST.
 * 응답은 dev-login 과 같은 모양(AuthResponse)에 inviteCode 를 더한 {@link ReviewLoginResponse} 다.
 *
 * <p>dev-login 과 달리 개발용 기능이 아니다. 심사위원이 운영 서버에서 구글 계정 없이 들어오는 길이라 운영에서 켜 두고,
 * DevFeatureGuard 목록에 넣지 않는다. 대신 남의 계정이 될 수는 없고(새 계정, 또는 새 계정 한도가 찼을 때 같은 IP 가 같은 kind 로 최근에
 * 만든 심사용 계정) IP 마다 한 시간에 60번까지다(kind 세 가지를 합쳐 센다). 새 계정은 모두 합쳐 한 시간에 300개까지 만든다(ReviewLoginLimiter).
 *
 * <p>끝나는 날(`app.auth.review-login.until`, prod 기본 2026-10-31)이 지나면 켜져 있어도 꺼진 것과 같게 404 NOT_FOUND 이고 계정을
 * 만들지 않는다. 심사가 끝난 뒤 끄는 것을 잊어도 누구나 계정을 만드는 길이 열려 있지 않게.
 */
@RestController
@ConditionalOnProperty(name = "app.auth.review-login.enabled", havingValue = "true")
public class ReviewLoginController {
    private final ReviewLoginService reviews;

    public ReviewLoginController(ReviewLoginService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/api/v1/auth/review-login")
    public ReviewLoginResponse reviewLogin(
            HttpServletRequest request, @RequestBody(required = false) @Nullable ReviewLoginRequest body) {
        if (!reviews.isOpen()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ReviewLoginKind kind = body == null ? ReviewLoginKind.FAMILY : body.kindOrDefault();
        return ReviewLoginResponse.of(reviews.login(request.getRemoteAddr(), kind));
    }
}
