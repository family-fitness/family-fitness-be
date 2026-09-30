package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 한 사람이 미션의 칸 하나를 처음 끝낸 기록(mission_session_completions 한 행). 넣기만 하고 고치지 않는다 —
 * 같은 칸을 다시 보내도 끝낸 날을 옮기지 않는다.
 *
 * @param position 칸 번호. 칸 없는 미션은 미션 전체를 한 칸(1)으로 받는다(결정 35)
 * @param completedOn 서버가 받은 날(KST). 여러 날짜리 미션이 어느 날 서는지를 이 날짜로 가른다
 * @param activeSeconds 인정한 영상 재생 초 — 요청 activeSeconds 를 endedAt − startedAt 으로 자른 값
 * @param verifiedBy 확인 방법. 칸 끝은 영상 재생 시간으로 인정하므로 VIDEO_PROGRESS 다(결정 3-1)
 */
public record SessionCompletion(
        UUID missionId,
        int position,
        UUID profileId,
        Instant completedAt,
        LocalDate completedOn,
        int activeSeconds,
        VerifiedBy verifiedBy) {
    /** 칸 끝 한 번에 받는 재생 초 상한(180분). 옛 타이머 기록의 분 상한(180)과 같다. */
    public static final int MAX_ACTIVE_SECONDS = 10_800;

    public SessionCompletion {
        if (position < 1) throw new IllegalArgumentException("칸 번호(position)는 1부터다");
        if (activeSeconds < 0) throw new IllegalArgumentException("인정한 초는 0 이상이어야 한다");
    }
}
