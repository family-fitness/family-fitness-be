package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import org.jspecify.annotations.Nullable;

/**
 * 미션 참여자 한 명. 끝냈는지는 사람마다다(FE 요청서 4장).
 *
 * @param doneSessions 이 사람이 끝낸 칸의 position, 오름차순. 칸 없는 미션을 칸 끝으로 끝냈으면 [1]
 */
public record MissionParticipantView(
        UUID profileId,
        @Nullable String name,
        double progress,
        boolean completed,
        @Nullable VerifiedBy verifiedBy,
        boolean needsGuardianCheck,
        List<Integer> doneSessions) {}
