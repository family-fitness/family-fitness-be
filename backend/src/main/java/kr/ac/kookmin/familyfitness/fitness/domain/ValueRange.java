package kr.ac.kookmin.familyfitness.fitness.domain;

/** 프론트 검증용 잠정 범위. 서버는 이 범위로 거부하지 않는다. */
public record ValueRange(int min, int max) {}
