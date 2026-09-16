package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;

public record CreatedFamily(UUID familyId, String familyName, ProfileSummary ownerProfile) {}
