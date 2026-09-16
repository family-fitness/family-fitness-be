package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record RejectCoachRunRequest(
        @Size(max = 300) @Nullable String reason) {}
