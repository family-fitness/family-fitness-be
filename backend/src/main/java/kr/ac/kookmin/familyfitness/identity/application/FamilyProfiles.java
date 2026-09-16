package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;

public record FamilyProfiles(UUID familyId, String familyName, List<ProfileSummary> profiles) {}
