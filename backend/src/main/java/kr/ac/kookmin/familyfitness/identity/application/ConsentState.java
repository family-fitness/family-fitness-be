package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ConsentState(
        boolean consentGiven,
        @Nullable Instant consentAt,
        @Nullable UUID consentBy,
        boolean measurable) {}
