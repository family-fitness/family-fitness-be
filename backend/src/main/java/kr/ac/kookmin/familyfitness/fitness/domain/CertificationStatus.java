package kr.ac.kookmin.familyfitness.fitness.domain;

/**
 * 인증 등급을 매겼는가.
 * <ul>
 *   <li>GRADED — 등급이 있다(참가 포함).
 *   <li>NEEDS_ITEMS — 그 나이 · 성별의 기준표 줄은 있는데, 어느 등급도 보는 항목을 다 재지 않아 판정하지 못했다.
 *   <li>NO_CRITERIA — 그 나이 · 성별의 기준표 줄이 하나도 없다(만 7~10세 · 어르신 등).
 * </ul>
 */
public enum CertificationStatus {
    GRADED,
    NEEDS_ITEMS,
    NO_CRITERIA
}
