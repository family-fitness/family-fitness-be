package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import kr.ac.kookmin.familyfitness.identity.application.InviteKind;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * 초대코드가 가리키는 자리. 식별자(profileId, familyId)는 싣지 않는다. invitedByName 은 보낸 보호자를 모르면 null.
 * kind 가 FAMILY(가족 초대)면 자리가 없어 profileName 과 ageGroup 이 null 이고, 화면은 이름과 생년월일을 받아 코드와 함께 보낸다.
 */
public record InvitePreviewResponse(
        InviteKind kind,
        String familyName,
        @Nullable String profileName,
        ProfileRole role,
        @Nullable AgeGroup ageGroup,
        @Nullable String invitedByName,
        Instant expiresAt) {}
