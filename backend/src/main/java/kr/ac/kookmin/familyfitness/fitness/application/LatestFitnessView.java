package kr.ac.kookmin.familyfitness.fitness.application;

import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import org.jspecify.annotations.Nullable;

/**
 * 최신 회차 조회 결과. {@code test} 가 null 이면 이력이 없다.
 * {@code parentScope} 가 false(호출 계정이 CHILD)면 웹 어댑터가 부모만 볼 값을 비운다 — {@link FitnessTestService} 참고.
 */
public record LatestFitnessView(@Nullable FitnessTest test, boolean parentScope) {}
