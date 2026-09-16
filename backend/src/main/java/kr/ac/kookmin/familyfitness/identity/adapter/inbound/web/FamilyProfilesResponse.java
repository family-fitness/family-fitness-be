package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;

public record FamilyProfilesResponse(UUID familyId, String familyName, List<ProfileSummary> profiles) {}
