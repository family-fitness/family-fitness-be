package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/*
 * 요청 본문. 필수 누락·형식 오류는 Bean Validation / Jackson 이 400 `BAD_REQUEST` 로 돌린다.
 * role·familyId 같은 권한 관련 값은 본문에서 읽더라도 신뢰하지 않는다 — 판단은 저장된 프로필로 한다.
 */
public record GoogleLoginRequest(
        @NotBlank String authorizationCode,
        @NotBlank String redirectUri,
        @Nullable String claimCode) {}
