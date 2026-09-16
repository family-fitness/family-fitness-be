package kr.ac.kookmin.familyfitness.coaching.domain;

/** `RUNNING` → `AWAITING_APPROVAL` → `APPROVED` / `REJECTED`; `RUNNING` → `FAILED`. */
public enum CoachRunStatus {
    RUNNING,
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    FAILED
}
