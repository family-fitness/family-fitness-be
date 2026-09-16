package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.AuthSession;
import kr.ac.kookmin.familyfitness.identity.application.NextStep;

public record MeResponse(UUID userId, NextStep nextStep, List<ProfileSummary> profiles) {
    public static MeResponse of(AuthSession session) {
        return new MeResponse(session.userId(), session.nextStep(), session.profiles());
    }
}
