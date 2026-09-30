package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;

/**
 * 운동 한 칸 끝 응답 — 부른 사람(profileId)의 진행이다(FE 요청서 3장).
 *
 * @param verifiedBy 이 칸의 확인 방법. 영상 재생 시간으로 인정하므로 VIDEO_PROGRESS(결정 3-1)
 * @param missionProgress 이 사람의 미션 진행도(끝낸 칸 분 합 ÷ 전체 칸 분 합)
 * @param xpGained 이번 요청으로 이 사람에게 새로 쌓인 경험치. /progress 가 실제로 는 만큼이고, 이미 끝낸 칸이면 0
 */
public record SessionCompletedView(
        int position, VerifiedBy verifiedBy, double missionProgress, boolean missionCompleted, int xpGained) {}
