package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/** 초대코드가 가리키는 자리. 식별자(profileId · familyId)는 싣지 않는다. invitedByName 은 보낸 보호자를 모르면 null. */
public record InvitePreviewResponse(
        String familyName,
        String profileName,
        ProfileRole role,
        AgeGroup ageGroup,
        @Nullable String invitedByName,
        Instant expiresAt) {}
