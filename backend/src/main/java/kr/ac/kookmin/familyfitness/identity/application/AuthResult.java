package kr.ac.kookmin.familyfitness.identity.application;

import kr.ac.kookmin.familyfitness.shared.security.ServiceTokens;

public record AuthResult(ServiceTokens tokens, AuthSession session) {}
