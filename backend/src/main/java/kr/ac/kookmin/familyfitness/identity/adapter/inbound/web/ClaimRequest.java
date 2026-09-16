package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimRequest(@NotBlank @Size(max = 20) String claimCode) {}
