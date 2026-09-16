package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import org.jspecify.annotations.Nullable;

public record MissionCreatedView(
        UUID missionId, MissionOrigin origin, @Nullable UUID coachRunId, boolean serverVerifiable) {}
