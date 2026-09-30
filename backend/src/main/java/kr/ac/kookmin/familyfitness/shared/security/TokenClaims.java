package kr.ac.kookmin.familyfitness.shared.security;

/** 토큰 클레임 이름과 값. 발급(identity)과 검증(shared)이 같은 상수를 본다. */
public final class TokenClaims {
    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";

    private TokenClaims() {}
}
