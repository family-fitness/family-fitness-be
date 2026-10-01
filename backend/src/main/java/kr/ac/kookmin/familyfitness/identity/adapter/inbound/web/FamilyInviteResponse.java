package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

/** 새로 만든 가족 초대. code 는 자리 초대코드와 같은 모양(6자리)이고 expiresAt 은 만든 때에서 7일 뒤다. */
public record FamilyInviteResponse(String code, ProfileRole role, Instant expiresAt, UUID familyId) {
    static FamilyInviteResponse of(FamilyInvite invite) {
        return new FamilyInviteResponse(
                invite.code().code(), invite.role(), invite.code().expiresAt(), invite.familyId());
    }
}
