package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.NextStep;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

public record ClaimResponse(UUID profileId, UUID familyId, ProfileRole role, NextStep nextStep) {}
