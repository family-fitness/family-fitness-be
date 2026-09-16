package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

// ---- GET /profiles/{profileId}/fitness-tests/latest ----
public record RadarPointResponse(
        FitnessFactor factor, @Nullable Integer percentile) {}
