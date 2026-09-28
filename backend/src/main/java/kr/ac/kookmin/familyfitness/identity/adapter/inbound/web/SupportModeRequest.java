package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

public record SupportModeRequest(@NotNull @Nullable SupportMode supportMode) {}
