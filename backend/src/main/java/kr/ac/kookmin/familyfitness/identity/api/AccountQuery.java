package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;

/** 다른 모듈이 계정 종류를 읽는 통로. */
@FunctionalInterface
public interface AccountQuery {
    /** 심사용 계정 로그인(POST /auth/review-login)이 만든 계정인지. 없는 계정이면 false. */
    boolean isReviewAccount(UUID userId);
}
