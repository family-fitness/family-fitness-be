package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * 초대코드가 가리키는 자리. 넣기 전에 「서준이네 · 아빠 자리」를 보여 줘 받는 사람이 역할을 고를 수 없다는 것을 화면이 알린다.
 * 식별자(profileId · familyId)는 싣지 않는다.
 *
 * @param invitedByName 코드를 보낸 보호자 이름. 발급자를 남기기 전에 만든 코드거나 그 프로필이 없으면 null
 */
public record InvitePreview(
        String familyName,
        String profileName,
        ProfileRole role,
        AgeGroup ageGroup,
        @Nullable String invitedByName,
        Instant expiresAt) {}
