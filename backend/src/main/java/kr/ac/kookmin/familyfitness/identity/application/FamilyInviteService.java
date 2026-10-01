package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyInviteRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.InviteNotFoundException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가족 초대(초대 먼저) 만들기, 목록, 취소. 보호자가 역할만 정해 코드를 내고, 이름과 생년월일은 코드로 들어온 사람이 넣는다. 코드
 * 미리 보기와 사용은 자리 초대코드와 같은 주소라 {@link InviteService} 가 맡는다.
 */
@Service
@Transactional
public class FamilyInviteService {
    private final FamilyRepository families;
    private final FamilyInviteRepository familyInvites;
    private final InviteCodes codes;
    private final IdentityClock clock;

    public FamilyInviteService(
            FamilyRepository families, FamilyInviteRepository familyInvites, InviteCodes codes, IdentityClock clock) {
        this.families = families;
        this.familyInvites = familyInvites;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * 판정 차례: 가족 없음 404 FAMILY_NOT_FOUND, 구성원 아님 403 NOT_SAME_FAMILY, 아이 계정 403 NOT_A_PARENT, CHILD 인데 동의가
     * 없거나 하나라도 false 면 422 CONSENT_REQUIRED({@link Family#issueFamilyInvite}).
     */
    public FamilyInvite create(
            UUID userId, UUID familyId, ProfileRole role, @Nullable GuardianConsent guardianConsent) {
        Family family = load(familyId);
        Instant now = clock.now();
        FamilyInvite invite =
                family.issueFamilyInvite(userId, role, guardianConsent, now, clock.today(), () -> codes.fresh(now));
        familyInvites.add(invite);
        return invite;
    }

    /**
     * 아직 쓰지 않았고 만료되지 않은 초대, 최근 것부터. 판정 차례: 가족 없음 404 FAMILY_NOT_FOUND, 구성원 아님 403 NOT_SAME_FAMILY,
     * 아이 계정 403 NOT_A_PARENT. 낸 보호자 이름은 같은 가족 프로필에서 읽는다(빠진 보호자의 초대는 오너에게 넘어가 있다).
     */
    @Transactional(readOnly = true)
    public List<LiveFamilyInvite> live(UUID userId, UUID familyId) {
        Family family = load(familyId);
        family.requireParent(userId);
        return familyInvites.liveOf(familyId, clock.now()).stream()
                .map(it -> new LiveFamilyInvite(
                        it.code().code(),
                        it.role(),
                        it.code().expiresAt(),
                        it.createdAt(),
                        family.profile(it.issuedByProfileId()).getDisplayName()))
                .toList();
    }

    /**
     * 아직 쓰지 않은 초대를 지운다. 그 가족 보호자면 누가 냈든 취소한다. 판정 차례: 가족 없음 404 FAMILY_NOT_FOUND, 구성원 아님 403
     * NOT_SAME_FAMILY, 아이 계정 403 NOT_A_PARENT, 그 가족에 쓰지 않은 그 코드가 없음 404 INVITE_NOT_FOUND. 만료된 초대도 지운다.
     */
    public void cancel(UUID userId, UUID familyId, String rawCode) {
        Family family = load(familyId);
        family.requireParent(userId);
        String code = ClaimCode.normalize(rawCode);
        if (!ClaimCode.isWellFormed(code) || !familyInvites.deleteUnclaimed(familyId, code)) {
            throw new InviteNotFoundException();
        }
    }

    private Family load(UUID familyId) {
        Family family = families.findById(familyId);
        if (family == null) throw new FamilyNotFoundException(familyId);
        return family;
    }
}
