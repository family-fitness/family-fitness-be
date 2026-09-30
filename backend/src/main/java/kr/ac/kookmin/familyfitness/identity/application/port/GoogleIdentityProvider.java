package kr.ac.kookmin.familyfitness.identity.application.port;

/** 구글 인가코드 → id_token 교환·검증. 네트워크는 어댑터가 맡고 application 은 결과만 본다. */
public interface GoogleIdentityProvider {
    GoogleIdentity exchange(String authorizationCode, String redirectUri);
}
