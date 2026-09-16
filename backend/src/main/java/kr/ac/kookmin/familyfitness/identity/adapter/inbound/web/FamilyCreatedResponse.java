package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;

public record FamilyCreatedResponse(UUID familyId, String familyName, ProfileSummary ownerProfile) {}
