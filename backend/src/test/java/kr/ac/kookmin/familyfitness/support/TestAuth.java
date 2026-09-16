package kr.ac.kookmin.familyfitness.support;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.security.ServiceTokenIssuer;
import org.springframework.stereotype.Component;

/** 웹 테스트용 Bearer 헤더 값. 실제 발급 경로({@link ServiceTokenIssuer})를 그대로 쓴다. */
@Component
public class TestAuth {
    private final ServiceTokenIssuer issuer;

    public TestAuth(ServiceTokenIssuer issuer) {
        this.issuer = issuer;
    }

    public String bearer(UUID userId) {
        return "Bearer " + issuer.issue(userId).accessToken();
    }
}
