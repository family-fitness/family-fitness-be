package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;

/**
 * 응원에 붙은 missionId 가 그 가족의 미션인지 묻는다.
 * 미션은 coaching 모듈이 갖고 있지만 identity 는 coaching 을 참조하지 않는다(coaching → identity::api 한 방향).
 * 그래서 인터페이스를 여기 두고 coaching 이 구현한다(의존 역전).
 */
public interface MissionLookup {
    /** 미션이 있고 그 미션의 가족이 {@code familyId} 이면 true. 없는 id 도 false 다. */
    boolean isFamilyMission(UUID familyId, UUID missionId);
}
