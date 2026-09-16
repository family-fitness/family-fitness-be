package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;

/** 입력 한 줄. 코드는 아직 검증 전이다. */
public record Measurement(String itemCode, BigDecimal value) {}
