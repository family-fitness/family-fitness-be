package kr.ac.kookmin.familyfitness.identity.application;

import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;

public record Invitation(ClaimCode claimCode, String shareUrl) {}
