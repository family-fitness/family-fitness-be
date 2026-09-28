package kr.ac.kookmin.familyfitness.progress.api;

/**
 * 칸 끝의 경험치 적립 · 업적 판정. coaching 이 칸 끝 트랜잭션 안에서 동기로 부른다(결정 26).
 * 부르기 전에 그 칸의 활동(activity)을 먼저 기록해 둔다 — 누적 분 업적(MIN_30 · MIN_100 · MIN_300)이 이번 분까지 센다.
 */
public interface ProgressRecorder {
    /**
     * 이번 호출로 새로 적립된 경험치 합. 칸 끝 응답의 {@code xpGained} 가 이 값이다.
     *
     * <pre>
     * 칸을 처음 끝냄                                   +5   키 "missionId:position"
     * 미션을 끝까지 했고 확인이 TIMER · VIDEO_PROGRESS  +20  키 "missionId"
     * </pre>
     *
     * 같은 (프로필, 종류, 키)는 원장에 한 번만 들어가므로 같은 칸을 다시 보내면 0 이다.
     */
    int sessionDone(SessionDone done);
}
