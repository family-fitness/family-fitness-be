package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * 가족 초대 만들기. guardianConsent 는 구성원 추가(AddMemberRequest)와 같은 모양이다. CHILD 면 꼭 보내고(둘 다 true),
 * PARENT 면 뺀다(보내도 남기지 않는다).
 */
public record CreateFamilyInviteRequest(
        @NotNull @Nullable ProfileRole role,
        @Valid @Nullable GuardianConsentRequest guardianConsent) {}
