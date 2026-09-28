package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * v2 테스트 우선 계약: 부모 계정 하나로 여러 아이를 관리한다.
 * HTTP 인증과 DB 원자성은 후속 통합 테스트로 검증한다.
 * 계약(api-contract §1)에 따라 생년월일·성별은 모든 프로필의 필수값이다.
 */
class FamilyTest {
    private final UUID parentUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final Instant consentedAt = Instant.parse("2026-09-08T10:00:00Z");
    private final LocalDate today = LocalDate.of(2026, 9, 8);
    private final LocalDate childBirthDate = LocalDate.of(2018, 5, 20);

    @Test
    @DisplayName("부모가 가족을 만들면 본인 계정과 연결된 부모 프로필이 생긴다")
    void 부모가_가족을_만들면_본인_계정과_연결된_부모_프로필이_생긴다() {
        Family family = newFamily();

        assertThat(family.getProfiles()).hasSize(1);
        Profile parent = family.getProfiles().getFirst();
        assertThat(parent.getUserId()).isEqualTo(parentUserId);
        assertThat(parent.getRole()).isEqualTo(ProfileRole.PARENT);
        assertThat(parent.isOwner()).isTrue();
        assertThat(parent.getFamilyId()).isEqualTo(family.getId());
        assertThat(parent.getSex()).isEqualTo(Sex.F);
        assertThat(family.getName()).isEqualTo("우리 가족");
    }

    @Test
    @DisplayName("아이 계정과 연락처 및 신체 측정 없이 아이 프로필을 추가한다")
    void 아이_계정과_연락처_및_신체_측정_없이_아이_프로필을_추가한다() {
        Family family = newFamily();

        Profile child = addChild(family, "첫째");

        assertThat(child.getUserId()).isNull();
        assertThat(child.getDisplayName()).isEqualTo("첫째");
        assertThat(child.getBirthDate()).isEqualTo(childBirthDate);
        assertThat(child.getSex()).isEqualTo(Sex.M);
        assertThat(child.getRole()).isEqualTo(ProfileRole.CHILD);
        assertThat(child.isOwner()).isFalse();
        assertThat(child.getHeightCm()).isNull();
        assertThat(child.getWeightKg()).isNull();
        assertThat(child.getSupportMode()).isNull();
        assertThat(family.getProfiles().stream().map(Profile::getId).toList()).contains(child.getId());
    }

    @Test
    @DisplayName("한 부모 계정으로 두 아이를 서로 다른 프로필로 관리한다")
    void 한_부모_계정으로_두_아이를_서로_다른_프로필로_관리한다() {
        Family family = newFamily();

        Profile first = addChild(family, "첫째");
        Profile second = addChild(family, "둘째");

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(family.getProfiles()).hasSize(3);
        assertThat(first.getUserId()).isNull();
        assertThat(second.getUserId()).isNull();
        assertThat(family.canManageProfile(parentUserId, first.getId())).isTrue();
        assertThat(family.canManageProfile(parentUserId, second.getId())).isTrue();
    }

    @Test
    @DisplayName("초대를 발급하지 않아도 부모가 아이 프로필을 관리할 수 있다")
    void 초대를_발급하지_않아도_부모가_아이_프로필을_관리할_수_있다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");

        assertThat(family.canManageProfile(parentUserId, child.getId())).isTrue();
        assertThat(child.getUserId()).isNull();
        assertThat(family.getProfiles().stream()
                        .filter(it -> parentUserId.equals(it.getUserId()))
                        .toList())
                .singleElement()
                .extracting(Profile::getRole)
                .isEqualTo(ProfileRole.PARENT);
    }

    @Test
    @DisplayName("다른 가족에서 부모인 계정도 이 가족의 아이를 관리할 수 없다")
    void 다른_가족에서_부모인_계정도_이_가족의_아이를_관리할_수_없다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        UUID otherParentId = UUID.randomUUID();
        Family otherFamily = Family.createWithParent(otherParentId, "다른 가족", "다른 부모", LocalDate.of(1985, 1, 1), Sex.M);

        assertThat(otherFamily.getProfiles())
                .singleElement()
                .extracting(Profile::getRole)
                .isEqualTo(ProfileRole.PARENT);
        assertThat(family.canManageProfile(otherParentId, child.getId())).isFalse();
        assertThrows(FamilyAccessDeniedException.class, () -> addChild(family, "추가 시도", otherParentId));
        assertThat(family.getProfiles()).hasSize(2);
    }

    @Test
    @DisplayName("같은 부모라도 현재 가족에 속하지 않은 프로필을 관리할 수 없다")
    void 같은_부모라도_현재_가족에_속하지_않은_프로필을_관리할_수_없다() {
        Family family = newFamily();

        assertThat(family.canManageProfile(parentUserId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("필요한 동의 없이 아이 프로필을 추가하지 않는다")
    void 필요한_동의_없이_아이_프로필을_추가하지_않는다() {
        Family family = newFamily();

        assertThrows(
                GuardianConsentRequiredException.class,
                () -> family.addChild(parentUserId, "아이", childBirthDate, Sex.M, false, false, consentedAt, today));
        assertThrows(
                GuardianConsentRequiredException.class,
                () -> family.addMember(
                        parentUserId,
                        "아이",
                        childBirthDate,
                        Sex.M,
                        ProfileRole.CHILD,
                        new GuardianConsent(true, false),
                        consentedAt,
                        today));
        assertThrows(
                GuardianConsentRequiredException.class,
                () -> family.addMember(
                        parentUserId, "아이", childBirthDate, Sex.M, ProfileRole.CHILD, null, consentedAt, today));

        assertThat(family.getProfiles()).hasSize(1);
    }

    @Test
    @DisplayName("만 14세 이상 구성원은 동의 없이 추가되고 동의 상태는 참으로 본다")
    void 만_14세_이상_구성원은_동의_없이_추가되고_동의_상태는_참으로_본다() {
        Family family = newFamily();

        Profile teen = family.addMember(
                parentUserId, "큰애", today.minusYears(14), Sex.F, ProfileRole.CHILD, null, consentedAt, today);

        assertThat(teen.consentRequired(today)).isFalse();
        assertThat(teen.consentGiven(today)).isTrue();
        assertThat(teen.measurable(today)).isTrue();
        assertThat(teen.getConsent().isGiven()).isFalse();
    }

    @Test
    @DisplayName("동의가 있는 아이는 측정 가능하고 서버가 동의 시각과 동의자를 채운다")
    void 동의가_있는_아이는_측정_가능하고_서버가_동의_시각과_동의자를_채운다() {
        Family family = newFamily();

        Profile child = addChild(family, "아이");

        assertThat(child.consentRequired(today)).isTrue();
        assertThat(child.consentGiven(today)).isTrue();
        assertThat(child.measurable(today)).isTrue();
        assertThat(child.getConsent().personalAt()).isEqualTo(consentedAt);
        assertThat(child.getConsent().healthAt()).isEqualTo(consentedAt);
        assertThat(child.getConsent().byUserId()).isEqualTo(parentUserId);
        assertThat(child.getConsent().revokedAt()).isNull();
    }

    @Test
    @DisplayName("만 4세 미만도 프로필은 만들어지지만 측정 대상은 아니다")
    void 만_4세_미만도_프로필은_만들어지지만_측정_대상은_아니다() {
        Family family = newFamily();

        Profile toddler =
                family.addChild(parentUserId, "막내", today.minusYears(2), Sex.M, true, true, consentedAt, today);

        assertThat(toddler.consentGiven(today)).isTrue();
        assertThat(toddler.measurable(today)).isFalse();
    }

    @Test
    @DisplayName("아이 계정은 구성원을 추가할 수 없다")
    void 아이_계정은_구성원을_추가할_수_없다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        UUID childUserId = UUID.randomUUID();
        child.claim(childUserId, consentedAt);

        assertThrows(NotAParentException.class, () -> addChild(family, "동생", childUserId));
        assertThat(family.canManageProfile(childUserId, child.getId())).isFalse();
    }

    @Test
    @DisplayName("동의를 철회하면 측정 불가가 되고 과거 동의 시각은 남는다")
    void 동의를_철회하면_측정_불가가_되고_과거_동의_시각은_남는다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        Instant revokedAt = consentedAt.plusSeconds(60);

        family.updateConsent(parentUserId, child.getId(), new GuardianConsent(true, false), revokedAt);

        assertThat(child.getConsent().isGiven()).isFalse();
        assertThat(child.getConsent().revokedAt()).isEqualTo(revokedAt);
        assertThat(child.getConsent().personalAt()).isEqualTo(consentedAt);
        assertThat(child.consentGiven(today)).isFalse();
        assertThat(child.measurable(today)).isFalse();

        Instant regrantedAt = revokedAt.plusSeconds(60);
        family.updateConsent(parentUserId, child.getId(), new GuardianConsent(true, true), regrantedAt);

        assertThat(child.getConsent().isGiven()).isTrue();
        assertThat(child.getConsent().personalAt()).isEqualTo(regrantedAt);
        assertThat(child.getConsent().revokedAt()).isNull();
        assertThat(child.measurable(today)).isTrue();
    }

    @Test
    @DisplayName("동의 변경은 이 가족의 부모만 할 수 있다")
    void 동의_변경은_이_가족의_부모만_할_수_있다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");

        assertThrows(
                FamilyAccessDeniedException.class,
                () -> family.updateConsent(
                        UUID.randomUUID(), child.getId(), new GuardianConsent(true, true), consentedAt));
        assertThrows(
                ProfileNotFoundException.class,
                () -> family.updateConsent(
                        parentUserId, UUID.randomUUID(), new GuardianConsent(true, true), consentedAt));
    }

    @Test
    @DisplayName("초대 상태는 코드 유무와 만료 및 사용 여부에서 파생된다")
    void 초대_상태는_코드_유무와_만료_및_사용_여부에서_파생된다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        Instant now = consentedAt;
        ClaimCode code = new ClaimCode("ABC234", now.plus(Duration.ofDays(7)));

        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.NONE);

        family.issueInvite(parentUserId, child.getId(), code);
        assertThat(child.getClaimCode()).isEqualTo(code);
        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.ISSUED);
        assertThat(child.inviteStatus(code.expiresAt())).isEqualTo(InviteStatus.EXPIRED);

        child.claim(UUID.randomUUID(), now.plusSeconds(10));
        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.CLAIMED);
        assertThat(child.inviteStatus(code.expiresAt().plusSeconds(1))).isEqualTo(InviteStatus.CLAIMED);
        assertThat(child.hasAccount()).isTrue();
    }

    @Test
    @DisplayName("초대 재발급은 이전 코드를 즉시 무효로 만들고 계정이 붙은 프로필에는 발급하지 않는다")
    void 초대_재발급은_이전_코드를_즉시_무효로_만들고_계정이_붙은_프로필에는_발급하지_않는다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        ClaimCode first = new ClaimCode("ABC234", consentedAt.plus(Duration.ofDays(7)));
        ClaimCode second = new ClaimCode("XYZ789", consentedAt.plus(Duration.ofDays(8)));

        family.issueInvite(parentUserId, child.getId(), first);
        family.issueInvite(parentUserId, child.getId(), second);
        assertThat(child.getClaimCode()).isEqualTo(second);

        UUID ownerProfileId = family.getProfiles().stream()
                .filter(Profile::isOwner)
                .findFirst()
                .orElseThrow()
                .getId();
        assertThrows(AlreadyClaimedException.class, () -> family.issueInvite(parentUserId, ownerProfileId, first));
        assertThrows(
                FamilyAccessDeniedException.class, () -> family.issueInvite(UUID.randomUUID(), child.getId(), first));
    }

    @Test
    @DisplayName("초대 코드 사용 규칙 - 만료·이미 사용·이미 구성원")
    void 초대_코드_사용_규칙_만료_이미_사용_이미_구성원() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        Instant now = consentedAt;
        ClaimCode code = new ClaimCode("ABC234", now.plus(Duration.ofDays(7)));
        UUID newcomer = UUID.randomUUID();

        assertThrows(ClaimCodeNotFoundException.class, () -> family.prepareClaim(child.getId(), newcomer, now));

        family.issueInvite(parentUserId, child.getId(), code);
        assertThat(family.prepareClaim(child.getId(), newcomer, now)).isSameAs(child);
        assertThrows(
                ClaimCodeExpiredException.class, () -> family.prepareClaim(child.getId(), newcomer, code.expiresAt()));
        assertThrows(AlreadyMemberException.class, () -> family.prepareClaim(child.getId(), parentUserId, now));

        child.claim(newcomer, now);
        assertThrows(AlreadyClaimedException.class, () -> family.prepareClaim(child.getId(), UUID.randomUUID(), now));
    }

    @Test
    @DisplayName("참여 수준은 본인 부모 프로필에만 바꿀 수 있다")
    void 참여_수준은_본인_부모_프로필에만_바꿀_수_있다() {
        Family family = newFamily();
        Profile parent = family.getProfiles().getFirst();
        Profile child = addChild(family, "아이");

        family.changeSupportMode(parentUserId, parent.getId(), SupportMode.WEEKEND);
        assertThat(parent.getSupportMode()).isEqualTo(SupportMode.WEEKEND);

        assertThrows(
                NotOwnProfileException.class,
                () -> family.changeSupportMode(parentUserId, child.getId(), SupportMode.FULL));
        assertThrows(
                NotOwnProfileException.class,
                () -> family.changeSupportMode(UUID.randomUUID(), parent.getId(), SupportMode.FULL));

        UUID childUserId = UUID.randomUUID();
        child.claim(childUserId, consentedAt);
        assertThrows(
                SupportModeNotApplicableException.class,
                () -> family.changeSupportMode(childUserId, child.getId(), SupportMode.FULL));
    }

    @Test
    @DisplayName("본인 프로필과 계정 없는 아이 프로필 이름으로만 행동할 수 있다")
    void 본인_프로필과_계정_없는_아이_프로필_이름으로만_행동할_수_있다() {
        Family family = newFamily();
        Profile parent = family.getProfiles().getFirst();
        Profile child = addChild(family, "첫째");
        Profile claimedChild = addChild(family, "둘째");
        UUID childUserId = UUID.randomUUID();
        claimedChild.claim(childUserId, consentedAt);
        Profile dadSeat = addParentSeat(family);

        assertThat(family.canActAs(parentUserId, parent.getId())).isTrue();
        assertThat(family.canActAs(parentUserId, child.getId())).isTrue();
        assertThat(family.canActAs(parentUserId, claimedChild.getId())).isFalse();
        assertThat(family.canActAs(parentUserId, dadSeat.getId())).isFalse();
        assertThat(family.canActAs(parentUserId, UUID.randomUUID())).isFalse();

        assertThat(family.canActAs(childUserId, claimedChild.getId())).isTrue();
        assertThat(family.canActAs(childUserId, child.getId())).isFalse();
        assertThat(family.canActAs(childUserId, parent.getId())).isFalse();

        assertThat(family.canActAs(UUID.randomUUID(), child.getId())).isFalse();
    }

    @Test
    @DisplayName("응원은 내가 행동할 수 있는 프로필에서 같은 가족의 다른 프로필로만 보낸다")
    void 응원은_내가_행동할_수_있는_프로필에서_같은_가족의_다른_프로필로만_보낸다() {
        Family family = newFamily();
        Profile parent = family.getProfiles().getFirst();
        Profile child = addChild(family, "아이");
        Profile dadSeat = addParentSeat(family);
        UUID stranger = UUID.randomUUID();

        family.validateCheer(parentUserId, parent.getId(), child.getId());
        family.validateCheer(parentUserId, child.getId(), parent.getId());

        assertThrows(
                FamilyAccessDeniedException.class, () -> family.validateCheer(stranger, parent.getId(), child.getId()));
        assertThrows(
                CannotActAsProfileException.class,
                () -> family.validateCheer(parentUserId, dadSeat.getId(), child.getId()));
        assertThrows(
                CannotActAsProfileException.class,
                () -> family.validateCheer(parentUserId, UUID.randomUUID(), child.getId()));
        assertThrows(
                SelfCheerException.class, () -> family.validateCheer(parentUserId, parent.getId(), parent.getId()));
        assertThrows(SelfCheerException.class, () -> family.validateCheer(parentUserId, child.getId(), child.getId()));
        assertThrows(
                NotFamilyMemberException.class,
                () -> family.validateCheer(parentUserId, parent.getId(), UUID.randomUUID()));

        UUID childUserId = UUID.randomUUID();
        child.claim(childUserId, consentedAt);
        family.validateCheer(childUserId, child.getId(), parent.getId());
        assertThrows(
                CannotActAsProfileException.class,
                () -> family.validateCheer(parentUserId, child.getId(), parent.getId()));
    }

    private Family newFamily() {
        return Family.createWithParent(parentUserId, "우리 가족", "부모", LocalDate.of(1988, 3, 1), Sex.F);
    }

    /** 아직 계정이 붙지 않은 부모 자리(초대 전 아빠). */
    private Profile addParentSeat(Family family) {
        return family.addMember(
                parentUserId, "아빠", LocalDate.of(1986, 1, 1), Sex.M, ProfileRole.PARENT, null, consentedAt, today);
    }

    private Profile addChild(Family family, String name) {
        return addChild(family, name, parentUserId);
    }

    private Profile addChild(Family family, String name, UUID actorUserId) {
        return family.addChild(actorUserId, name, childBirthDate, Sex.M, true, true, consentedAt, today);
    }
}
