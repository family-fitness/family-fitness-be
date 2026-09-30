package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;

public record CreatedMissionView(UUID missionId, String title, MissionOrigin origin) {}
