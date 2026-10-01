package kr.ac.kookmin.familyfitness.identity.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import kr.ac.kookmin.familyfitness.identity.application.port.IdentityErasureRepository;

/**
 * 남는 응원의 missionId 가 지운 미션을 가리키면 비운다. 탈퇴, 내보내기, 동의 철회 때 coaching 이 그 사람만 참여한 미션을 통째로
 * 지웠을 수 있다. 미션 표는 coaching 것이라 미션이 남아 있는지는 {@link MissionLookup} 으로 묻는다. 부르는 쪽 트랜잭션 안에서 돈다.
 */
final class DeletedMissionsOnCheers {
    private DeletedMissionsOnCheers() {}

    static void forget(IdentityErasureRepository erasure, MissionLookup missions, UUID familyId) {
        for (UUID missionId : erasure.missionsOnCheers(familyId)) {
            if (!missions.isFamilyMission(familyId, missionId)) erasure.forgetMissionOnCheers(familyId, missionId);
        }
    }
}
