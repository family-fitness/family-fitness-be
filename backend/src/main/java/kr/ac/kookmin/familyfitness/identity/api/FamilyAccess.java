package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 가족 단위 권한 판단. HTTP 요청의 role·familyId 를 믿지 않고 identity 저장소에서 판단한다.
 * 실패 시 {@link NotSameFamilyException} / {@link NotAParentException} 을 던진다.
 */
public interface FamilyAccess {
    /** 계정이 이 가족의 구성원인지. 구성원이면 그 계정의 프로필 요약을 돌려준다. */
    ProfileSummary requireMember(UUID userId, UUID familyId);

    /** 계정이 이 가족의 PARENT 인지. 부모 프로필 요약을 돌려준다. */
    ProfileSummary requireParent(UUID userId, UUID familyId);

    @Nullable
    ProfileSummary memberOf(UUID userId, UUID familyId);

    /** 대상 프로필과 같은 가족의 구성원인지. 대상 프로필 요약을 돌려준다. 없으면 {@link ProfileNotFoundException}. */
    ProfileSummary requireSameFamilyAsProfile(UUID userId, UUID profileId);

    /** 대상 프로필이 속한 가족의 PARENT 인지. 호출자(부모) 프로필 요약을 돌려준다. */
    ProfileSummary requireParentOfProfile(UUID userId, UUID profileId);

    /**
     * 계정이 이 프로필 이름으로 행동할 수 있는지. 대상 프로필 요약을 돌려준다.
     * 본인 계정에 붙은 프로필이거나, 같은 가족의 PARENT 가 계정 없는 CHILD 프로필을 대신할 때만 된다.
     * 프로필이 없으면 {@link ProfileNotFoundException}, 가족이 아니면 {@link NotSameFamilyException},
     * 그 밖은 {@link CannotActAsProfileException}.
     */
    ProfileSummary requireActingAs(UUID userId, UUID profileId);
}
