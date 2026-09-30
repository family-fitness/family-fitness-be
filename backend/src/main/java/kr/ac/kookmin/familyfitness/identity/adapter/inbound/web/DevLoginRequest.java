package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record DevLoginRequest(
        @NotBlank @Size(max = 191) String providerUserId,
        @Size(max = 255) @Nullable String email,
        @Nullable String claimCode) {}
