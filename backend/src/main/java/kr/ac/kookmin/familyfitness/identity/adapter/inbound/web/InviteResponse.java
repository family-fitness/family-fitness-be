package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;

public record InviteResponse(String claimCode, Instant expiresAt, String shareUrl) {}
