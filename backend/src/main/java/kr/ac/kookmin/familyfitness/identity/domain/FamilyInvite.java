package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * 가족 초대(초대 먼저). 보호자가 역할만 정해 낸 코드이고({@link Family#issueFamilyInvite}), 이름과 생년월일은 코드로 들어온
 * 사람이 넣는다.
 * 보호자가 정보를 먼저 넣어 만든 자리의 초대코드({@link Profile#getClaimCode})와 모양과 만료가 같다. 한 번만 쓴다.
 *
 * @param guardianConsent CHILD 초대를 만들 때 보호자가 보낸 동의. 늘 둘 다 true 다. PARENT 초대는 null
 * @param consentByUserId 그 동의를 한 보호자 계정. 그 계정이 가족에서 빠지면 null 이 된다(누가 했는지만 모르는 채로 남는다)
 * @param issuedByProfileId 초대를 낸 보호자 프로필. 그 사람이 가족에서 빠지면 오너 프로필로 바뀐다
 * @param claimedByUserId 코드를 쓴 계정. 쓰지 않았거나 쓴 계정이 탈퇴했으면 null
 */
public record FamilyInvite(
        ClaimCode code,
        UUID familyId,
        ProfileRole role,
        @Nullable GuardianConsent guardianConsent,
        @Nullable UUID consentByUserId,
        UUID issuedByProfileId,
        Instant createdAt,
        @Nullable Instant claimedAt,
        @Nullable UUID claimedByUserId) {
    public FamilyInvite {
        if (role == ProfileRole.CHILD && (guardianConsent == null || !guardianConsent.isComplete())) {
            throw new IllegalArgumentException("CHILD 초대에는 보호자 동의가 둘 다 있어야 한다");
        }
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }

    /** 아직 쓰지 않았고 만료 전이다. 목록에 보이는 초대다. */
    public boolean isLive(Instant now) {
        return !isClaimed() && !code.isExpired(now);
    }

    /** 미리 보기와 사용의 판정. 차례: 이미 사용 409 ALREADY_CLAIMED, 만료 410 CODE_EXPIRED. */
    public void requireClaimable(Instant now) {
        if (isClaimed()) throw new AlreadyClaimedException("이미 쓴 초대코드입니다");
        if (code.isExpired(now)) throw new ClaimCodeExpiredException();
    }
}
