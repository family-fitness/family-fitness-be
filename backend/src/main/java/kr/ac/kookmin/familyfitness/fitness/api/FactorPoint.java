package kr.ac.kookmin.familyfitness.fitness.api;

import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;

public record FactorPoint(FitnessFactor factor, String itemCode, int percentile) {}
