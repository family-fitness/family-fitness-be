package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import org.jspecify.annotations.Nullable;

/** 로그아웃 본문. 토큰이 없어도 400 이 아니라 204 다(멱등) — 그래서 검증 어노테이션을 달지 않는다. */
public record LogoutRequest(@Nullable String refreshToken) {}
