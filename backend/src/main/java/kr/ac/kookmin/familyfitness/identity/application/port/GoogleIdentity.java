package kr.ac.kookmin.familyfitness.identity.application.port;

import org.jspecify.annotations.Nullable;

/** 구글이 검증해 준 사용자. `subject` 가 providerUserId 가 된다. */
public record GoogleIdentity(String subject, @Nullable String email) {}
