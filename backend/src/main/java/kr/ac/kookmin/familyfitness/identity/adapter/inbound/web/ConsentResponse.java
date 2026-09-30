package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ConsentResponse(
        boolean consentGiven,
        @Nullable Instant consentAt,
        @Nullable UUID consentBy,
        boolean measurable) {}
