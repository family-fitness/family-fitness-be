package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFamilyRequest(
        @NotBlank @Size(min = 1, max = 20) String familyName,
        @Valid OwnerRequest owner) {}
