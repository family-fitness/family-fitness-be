package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

public record ClaimResult(UUID profileId, UUID familyId, ProfileRole role, NextStep nextStep) {}
