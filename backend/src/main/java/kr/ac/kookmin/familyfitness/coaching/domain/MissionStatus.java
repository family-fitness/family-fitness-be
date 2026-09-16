package kr.ac.kookmin.familyfitness.coaching.domain;

/** 목록 필터. `ACTIVE` = 오늘 ≤ endDate 이고 전원 완료 아님 · `DONE` = 전원 완료 · `EXPIRED` = endDate 지났고 미완료. */
public enum MissionStatus {
    ACTIVE,
    DONE,
    EXPIRED
}
