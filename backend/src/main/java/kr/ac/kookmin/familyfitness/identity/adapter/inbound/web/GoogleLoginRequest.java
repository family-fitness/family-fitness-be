package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/*
 * 요청 본문. 필수 누락·형식 오류는 Bean Validation / Jackson 이 400 `BAD_REQUEST` 로 돌린다.
 * role·familyId 같은 권한 관련 값은 본문에서 읽더라도 신뢰하지 않는다 — 판단은 저장된 프로필로 한다.
 * utmSource, utmMedium, utmCampaign 은 FE 가 처음 들어온 주소에서 기억해 둔 utm 값이다(유입 경로, V174). 처음 가입할 때만 남고,
 * 서버가 앞뒤 공백을 걷고 64자로 자른다. 터무니없이 긴 값은 400 으로 막는다.
 */
public record GoogleLoginRequest(
        @NotBlank String authorizationCode,
        @NotBlank String redirectUri,
        @Nullable String claimCode,
        @Nullable @Size(max = 200) String utmSource,
        @Nullable @Size(max = 200) String utmMedium,
        @Nullable @Size(max = 200) String utmCampaign) {}
