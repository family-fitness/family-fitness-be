package kr.ac.kookmin.familyfitness.activity.api;

/** 기간 합계. 분은 초 합을 60 으로 나눠 내림한 값이다. */
public record ActivityTotals(
        int steps,
        /* 모든 출처 합계 — 「사람이 말한 활동」 포함 */
        int activeMinutes,
        /* TIMER + VIDEO — 「서버가 아는 활동」 */
        int verifiedMinutes) {}
