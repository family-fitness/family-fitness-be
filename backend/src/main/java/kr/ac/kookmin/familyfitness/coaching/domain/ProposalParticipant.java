package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

public record ProposalParticipant(UUID profileId, ProfileRole role, String coachRole) {}
