package kr.ac.kookmin.familyfitness.coaching.api;

import java.util.List;
import java.util.UUID;

/**
 * 탈퇴나 구성원 내보내기로 미션을 통째로 지웠다(참여자가 지우는 사람 하나뿐이던 미션). 지우는 트랜잭션 안에서 발행하고, 알림은
 * 같은 트랜잭션에서 동기로 듣고 이 미션들의 알림을 지운다. 보호자가 미션 하나를 지울 때 내는 {@link MissionCancelled} 와 달리
 * 커밋 뒤에 듣지 않는다. 지우기가 되돌아가면 알림도 같이 되돌아가야 해서다.
 */
public record MissionsErased(UUID familyId, List<UUID> missionIds) {
    public MissionsErased {
        missionIds = List.copyOf(missionIds);
    }
}
