package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;

/** 계정과 그 계정에 붙은 프로필들. `/me` 와 인증 응답이 공유하는 모양. */
public record AuthSession(UUID userId, NextStep nextStep, List<ProfileSummary> profiles) {}
