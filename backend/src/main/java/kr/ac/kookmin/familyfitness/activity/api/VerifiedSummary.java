package kr.ac.kookmin.familyfitness.activity.api;

/**
 * 지금까지 서버가 잰 활동(TIMER + VIDEO)의 요약. 기간 제한이 없다.
 *
 * @param activeDays 서버가 잰 초가 0 보다 큰 날 수 — 「운동한 날」(키움 섬의 나무 수)
 * @param totalMinutes 서버가 잰 초의 합을 60 으로 나눠 내림한 분
 */
public record VerifiedSummary(int activeDays, int totalMinutes) {}
