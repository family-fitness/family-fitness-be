package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.api.MissionCreated;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;

/** 미션에서 coaching.api 이벤트를 만든다. 직접 만들기 · 여러 날 만들기 · 제안 승인이 같은 모양으로 낸다(결정 48). */
final class MissionEvents {
    private MissionEvents() {}

    static MissionCreated created(Mission mission) {
        return new MissionCreated(
                mission.getId(),
                mission.getFamilyId(),
                mission.getTitle(),
                mission.getStartsOn(),
                mission.getEndsOn(),
                mission.participantProfileIds(),
                mission.getCreatedAt());
    }
}
