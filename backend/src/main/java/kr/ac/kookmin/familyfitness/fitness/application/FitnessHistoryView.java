package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;

/**
 * 측정 이력 조회 결과. 최근 회차가 먼저 온다.
 * {@code parentScope} 가 false(호출 계정이 CHILD)면 웹 어댑터가 체중을 비운다 — {@link FitnessTestService} 참고.
 */
public record FitnessHistoryView(List<FitnessTest> tests, boolean parentScope) {}
