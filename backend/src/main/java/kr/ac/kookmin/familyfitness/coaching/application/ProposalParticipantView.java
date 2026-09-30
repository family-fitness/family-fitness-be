package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

public record ProposalParticipantView(UUID profileId, ProfileRole role, String coachRole) {}
