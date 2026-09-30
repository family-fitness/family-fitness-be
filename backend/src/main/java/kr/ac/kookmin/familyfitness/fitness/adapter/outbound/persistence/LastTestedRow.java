package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.UUID;

/** JPQL 한 줄 — 프로필과 그 프로필의 가장 늦은 측정일. */
public record LastTestedRow(UUID profileId, LocalDate testedOn) {}
