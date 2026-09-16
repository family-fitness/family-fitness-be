package kr.ac.kookmin.familyfitness.identity.api;

import java.time.Instant;
import java.util.UUID;

/** 응원 집계(주간 요약용). */
public interface CheerQuery {
    int countCheers(UUID familyId, Instant from, Instant to);
}
