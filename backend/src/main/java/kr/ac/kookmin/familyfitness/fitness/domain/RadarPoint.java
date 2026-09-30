package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

public record RadarPoint(FitnessFactor factor, @Nullable Integer percentile) {}
