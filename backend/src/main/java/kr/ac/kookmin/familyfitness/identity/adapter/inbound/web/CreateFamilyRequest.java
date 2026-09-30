package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateFamilyRequest(
        @NotBlank @Size(min = 1, max = 20) String familyName,
        /* @Valid 는 null 이면 안을 보지 않는다 — @NotNull 이 따로 있어야 빠졌을 때 400 이 된다. */
        @NotNull @Valid @Nullable OwnerRequest owner) {}
