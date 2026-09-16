package kr.ac.kookmin.familyfitness.activity.api;

public record ActivityTotals(
        int steps,
        /** 모든 출처 합계 — 「사람이 말한 활동」 포함 */
        int activeMinutes,
        /** TIMER + VIDEO — 「서버가 아는 활동」 */
        int verifiedMinutes) {}
