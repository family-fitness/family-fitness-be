package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;
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
    @DisplayName("구성원을 추가할 때 적은 키 · 몸무게를 프로필에 담는다")
    void 구성원을_추가할_때_적은_키_몸무게를_프로필에_담는다() {
        Family family = newFamily();

        Profile child = family.addMember(
                parentUserId,
                "아이",
                childBirthDate,
                Sex.M,
                ProfileRole.CHILD,
                new BigDecimal("128.5"),
                new BigDecimal("27.3"),
                new GuardianConsent(true, true),
                consentedAt,
                today);

        assertThat(child.getHeightCm()).isEqualByComparingTo("128.5");
        assertThat(child.getWeightKg()).isEqualByComparingTo("27.3");
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
        Family otherFamily =
                Family.createWithParent(otherParentId, "다른 가족", "다른 부모", LocalDate.of(1985, 1, 1), Sex.M, today);

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
                        null,
                        null,
                        new GuardianConsent(true, false),
                        consentedAt,
                        today));
        assertThrows(
                GuardianConsentRequiredException.class,
                () -> family.addMember(
                        parentUserId,
                        "아이",
                        childBirthDate,
                        Sex.M,
                        ProfileRole.CHILD,
                        null,
                        null,
                        null,
                        consentedAt,
                        today));

        assertThat(family.getProfiles()).hasSize(1);
    }

    @Test
    @DisplayName("만 14세 이상 구성원은 동의 없이 추가되고 동의 상태는 참으로 본다")
    void 만_14세_이상_구성원은_동의_없이_추가되고_동의_상태는_참으로_본다() {
        Family family = newFamily();

        Profile teen = family.addMember(
                parentUserId,
                "큰애",
                today.minusYears(14),
                Sex.F,
                ProfileRole.CHILD,
                null,
                null,
                null,
                consentedAt,
                today);

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

        family.updateConsent(parentUserId, child.getId(), new GuardianConsent(true, false), revokedAt, today);

        assertThat(child.getConsent().isGiven()).isFalse();
        assertThat(child.getConsent().revokedAt()).isEqualTo(revokedAt);
        assertThat(child.getConsent().personalAt()).isEqualTo(consentedAt);
        assertThat(child.consentGiven(today)).isFalse();
        assertThat(child.measurable(today)).isFalse();

        Instant regrantedAt = revokedAt.plusSeconds(60);
        family.updateConsent(parentUserId, child.getId(), new GuardianConsent(true, true), regrantedAt, today);

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
                        UUID.randomUUID(), child.getId(), new GuardianConsent(true, true), consentedAt, today));
        assertThrows(
                ProfileNotFoundException.class,
                () -> family.updateConsent(
                        parentUserId, UUID.randomUUID(), new GuardianConsent(true, true), consentedAt, today));
    }

    @Test
    @DisplayName("초대 상태는 코드 유무와 만료 및 사용 여부에서 파생된다")
    void 초대_상태는_코드_유무와_만료_및_사용_여부에서_파생된다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        Instant now = consentedAt;
        ClaimCode code = new ClaimCode("ABC234", now.plus(Duration.ofDays(7)));

        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.NONE);

        family.issueInvite(parentUserId, child.getId(), now, () -> code);
        assertThat(child.getClaimCode()).isEqualTo(code);
        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.ISSUED);
        assertThat(child.inviteStatus(code.expiresAt())).isEqualTo(InviteStatus.EXPIRED);

        child.claim(UUID.randomUUID(), now.plusSeconds(10));
        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.CLAIMED);
        assertThat(child.inviteStatus(code.expiresAt().plusSeconds(1))).isEqualTo(InviteStatus.CLAIMED);
        assertThat(child.hasAccount()).isTrue();
    }

    @Test
    @DisplayName("살아 있는 코드가 있으면 다시 발급해도 같은 코드이고, 만료된 뒤에만 새 코드다. 보낸 보호자를 남긴다")
    void 살아_있는_코드가_있으면_다시_발급해도_같은_코드이고_만료된_뒤에만_새_코드다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        UUID ownerProfileId = family.memberOf(parentUserId).getId();
        ClaimCode first = new ClaimCode("ABC234", consentedAt.plus(Duration.ofDays(7)));
        ClaimCode second = new ClaimCode("XYZ789", first.expiresAt().plus(Duration.ofDays(7)));

        assertThat(family.issueInvite(parentUserId, child.getId(), consentedAt, () -> first))
                .isEqualTo(first);
        assertThat(child.getClaimCodeIssuedBy()).isEqualTo(ownerProfileId);
        assertThat(family.issueInvite(
                        parentUserId, child.getId(), first.expiresAt().minusSeconds(1), () -> second))
                .isEqualTo(first);
        assertThat(child.getClaimCode()).isEqualTo(first);

        assertThat(family.issueInvite(parentUserId, child.getId(), first.expiresAt(), () -> second))
                .isEqualTo(second);
        assertThat(child.getClaimCode()).isEqualTo(second);

        assertThrows(
                AlreadyClaimedException.class,
                () -> family.issueInvite(parentUserId, ownerProfileId, consentedAt, () -> first));
        assertThrows(
                FamilyAccessDeniedException.class,
                () -> family.issueInvite(UUID.randomUUID(), child.getId(), consentedAt, () -> first));
    }

    @Test
    @DisplayName("초대 코드 사용 규칙 - 없음 → 이미 사용 → 만료 → 이미 구성원 → 다른 가족")
    void 초대_코드_사용_규칙_없음_이미_사용_만료_이미_구성원_다른_가족() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        Instant now = consentedAt;
        ClaimCode code = new ClaimCode("ABC234", now.plus(Duration.ofDays(7)));
        UUID newcomer = UUID.randomUUID();

        assertThrows(ClaimCodeNotFoundException.class, () -> family.prepareClaim(child.getId(), newcomer, now, false));

        family.issueInvite(parentUserId, child.getId(), now, () -> code);
        assertThat(family.prepareClaim(child.getId(), newcomer, now, false)).isSameAs(child);
        assertThrows(
                ClaimCodeExpiredException.class,
                () -> family.prepareClaim(child.getId(), newcomer, code.expiresAt(), false));
        // 이 가족 구성원이면 다른 가족 검사보다 먼저 ALREADY_MEMBER 다
        assertThrows(AlreadyMemberException.class, () -> family.prepareClaim(child.getId(), parentUserId, now, true));
        assertThrows(AlreadyInFamilyException.class, () -> family.prepareClaim(child.getId(), newcomer, now, true));
        // 만료가 다른 가족 검사보다 먼저다
        assertThrows(
                ClaimCodeExpiredException.class,
                () -> family.prepareClaim(child.getId(), newcomer, code.expiresAt(), true));

        child.claim(newcomer, now);
        assertThrows(
                AlreadyClaimedException.class,
                () -> family.prepareClaim(child.getId(), UUID.randomUUID(), code.expiresAt(), true));
    }

    @Test
    @DisplayName("미리 보기 판정은 없음 → 이미 사용 → 만료이고 구성원인지는 보지 않는다")
    void 미리_보기_판정은_없음_이미_사용_만료이고_구성원인지는_보지_않는다() {
        Family family = newFamily();
        Profile child = addChild(family, "아이");
        ClaimCode code = new ClaimCode("ABC234", consentedAt.plus(Duration.ofDays(7)));

        assertThrows(ClaimCodeNotFoundException.class, () -> family.claimableSeat(child.getId(), consentedAt));
        family.issueInvite(parentUserId, child.getId(), consentedAt, () -> code);
        assertThat(family.claimableSeat(child.getId(), consentedAt)).isSameAs(child);
        assertThrows(ClaimCodeExpiredException.class, () -> family.claimableSeat(child.getId(), code.expiresAt()));

        child.claim(UUID.randomUUID(), consentedAt);
        assertThrows(AlreadyClaimedException.class, () -> family.claimableSeat(child.getId(), code.expiresAt()));
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

    @Test
    @DisplayName("만 14세 미만은 가족을 만들지 못하고 PARENT 로도 들어오지 못한다. 만 14세 생일부터는 된다")
    void 만_14세_미만은_가족을_만들지_못하고_PARENT_로도_들어오지_못한다() {
        LocalDate thirteen = today.minusYears(14).plusDays(1);
        LocalDate fourteen = today.minusYears(14);

        assertThrows(
                Under14NotAllowedException.class,
                () -> Family.createWithParent(UUID.randomUUID(), "아이 가족", "아이", thirteen, Sex.F, today));
        assertThat(Family.createWithParent(UUID.randomUUID(), "큰애 가족", "큰애", fourteen, Sex.F, today)
                        .getProfiles())
                .hasSize(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> Family.createWithParent(UUID.randomUUID(), "미래", "미래", today.plusDays(1), Sex.F, today));

        Family family = newFamily();
        assertThrows(
                Under14NotAllowedException.class,
                () -> family.addMember(
                        parentUserId,
                        "어린 보호자",
                        thirteen,
                        Sex.M,
                        ProfileRole.PARENT,
                        null,
                        null,
                        new GuardianConsent(true, true),
                        consentedAt,
                        today));
        // 같은 나이라도 CHILD 는 동의와 함께 들어온다
        Profile child = family.addChild(parentUserId, "아이", thirteen, Sex.M, true, true, consentedAt, today);
        assertThat(child.getRole()).isEqualTo(ProfileRole.CHILD);
        assertThat(family.getProfiles()).hasSize(2);
    }

    @Test
    @DisplayName("자기 프로필의 동의는 주지도 거두지도 못하고, 만 14세 미만 보호자는 남의 동의도 바꾸지 못한다")
    void 자기_프로필의_동의는_바꾸지_못하고_만_14세_미만_보호자는_동의를_바꾸지_못한다() {
        Family family = newFamily();
        Profile parent = family.getProfiles().getFirst();
        Profile child = addChild(family, "아이");

        assertThrows(
                SelfConsentException.class,
                () -> family.updateConsent(
                        parentUserId, parent.getId(), new GuardianConsent(true, true), consentedAt, today));
        assertThrows(
                SelfConsentException.class,
                () -> family.updateConsent(
                        parentUserId, parent.getId(), new GuardianConsent(false, false), consentedAt, today));
        assertThat(parent.getConsent()).isEqualTo(ConsentRecord.NONE);

        // 막힌 시도는 이력을 남기지 않는다(아이 추가 때의 GRANTED 한 줄만)
        assertThat(family.drainConsentEvents()).hasSize(1);

        // 이 규칙이 생기기 전에 만 14세 미만이 owner 로 가족을 만든 경우(지난 데이터)
        UUID familyId = UUID.randomUUID();
        UUID kidOwner = UUID.randomUUID();
        Profile sibling = legacyProfile(familyId, null, ProfileRole.CHILD, today.minusYears(9));
        Family kidFamily = Family.restore(
                familyId,
                "아이 가족",
                List.of(legacyProfile(familyId, kidOwner, ProfileRole.PARENT, today.minusYears(11)), sibling));
        assertThrows(
                Under14NotAllowedException.class,
                () -> kidFamily.updateConsent(
                        kidOwner, sibling.getId(), new GuardianConsent(true, true), consentedAt, today));
        assertThat(sibling.getConsent()).isEqualTo(ConsentRecord.NONE);
        assertThat(kidFamily.drainConsentEvents()).isEmpty();
    }

    private Profile legacyProfile(UUID familyId, @Nullable UUID userId, ProfileRole role, LocalDate birthDate) {
        return new Profile(
                UUID.randomUUID(),
                familyId,
                userId,
                role,
                role == ProfileRole.PARENT,
                "아이",
                birthDate,
                Sex.F,
                null,
                null,
                null,
                null,
                null,
                null,
                ConsentRecord.NONE);
    }

    @Test
    @DisplayName("거둔 동의는 만 14세 생일이 지나도 풀리지 않고, 보호자가 다시 동의해야 풀린다")
    void 거둔_동의는_만_14세_생일이_지나도_풀리지_않는다() {
        Family family = newFamily();
        LocalDate birth = today.minusYears(14).plusDays(10);
        Profile teen = family.addChild(parentUserId, "큰애", birth, Sex.F, true, true, consentedAt, today);
        family.updateConsent(parentUserId, teen.getId(), new GuardianConsent(false, false), consentedAt, today);
        LocalDate afterBirthday = today.plusDays(10);

        assertThat(teen.consentRequired(afterBirthday)).isTrue();
        assertThat(teen.consentGiven(afterBirthday)).isFalse();
        assertThat(teen.measurable(afterBirthday)).isFalse();

        family.updateConsent(
                parentUserId,
                teen.getId(),
                new GuardianConsent(true, true),
                consentedAt.plusSeconds(60),
                afterBirthday);
        assertThat(teen.consentRequired(afterBirthday)).isFalse();
        assertThat(teen.consentGiven(afterBirthday)).isTrue();
        assertThat(teen.measurable(afterBirthday)).isTrue();

        // 거둔 적 없는 만 14세 이상은 전과 같이 동의 없이도 된다
        Profile adult = family.addMember(
                parentUserId,
                "큰형",
                today.minusYears(15),
                Sex.M,
                ProfileRole.CHILD,
                null,
                null,
                null,
                consentedAt,
                today);
        assertThat(adult.consentRequired(today)).isFalse();
        assertThat(adult.consentGiven(today)).isTrue();
        // 만 14세 이상도 보호자가 거두면 다시 동의할 때까지 막힌다(FE 목과 같다)
        family.updateConsent(parentUserId, adult.getId(), new GuardianConsent(false, false), consentedAt, today);
        assertThat(adult.consentRequired(today)).isTrue();
        assertThat(adult.consentGiven(today)).isFalse();
        assertThat(adult.measurable(today)).isFalse();
    }

    @Test
    @DisplayName("동의를 주고 거둘 때마다 이력이 한 줄씩 쌓이고, 재동의가 철회 줄을 지우지 않는다")
    void 동의를_주고_거둘_때마다_이력이_한_줄씩_쌓이고_재동의가_철회_줄을_지우지_않는다() {
        Family family = newFamily();
        UUID dadUserId = UUID.randomUUID();
        Profile dadSeat = addParentSeat(family);
        dadSeat.claim(dadUserId, consentedAt);
        Profile child = addChild(family, "아이");
        Instant revokedAt = consentedAt.plusSeconds(60);
        Instant regrantedAt = consentedAt.plusSeconds(120);

        family.updateConsent(parentUserId, child.getId(), new GuardianConsent(true, false), revokedAt, today);
        family.updateConsent(dadUserId, child.getId(), new GuardianConsent(true, true), regrantedAt, today);

        assertThat(family.drainConsentEvents())
                .containsExactly(
                        new ConsentEvent(
                                child.getId(), parentUserId, ConsentEvent.Kind.GRANTED, true, true, consentedAt),
                        new ConsentEvent(
                                child.getId(), parentUserId, ConsentEvent.Kind.REVOKED, true, false, revokedAt),
                        new ConsentEvent(child.getId(), dadUserId, ConsentEvent.Kind.GRANTED, true, true, regrantedAt));
        // 꺼내면 비워진다 — 같은 줄을 두 번 넣지 않는다
        assertThat(family.drainConsentEvents()).isEmpty();
        // 동의 없이 들어온 만 14세 이상 · 부모 자리는 이력을 남기지 않는다
        addParentSeat(family);
        assertThat(family.drainConsentEvents()).isEmpty();
    }

    @Test
    @DisplayName("프로필 고치기 — 보호자가 계정 없는 프로필과 자기 프로필의 이름 · 생년월일 · 성별을 고친다")
    void 프로필_고치기_보호자가_계정_없는_프로필과_자기_프로필을_고친다() {
        Family family = newFamily();
        Profile parent = family.getProfiles().getFirst();
        Profile teen = family.addMember(
                parentUserId,
                "큰애",
                today.minusYears(20),
                Sex.F,
                ProfileRole.CHILD,
                null,
                null,
                null,
                consentedAt,
                today);
        assertThat(teen.consentRequired(today)).isFalse();
        assertThat(teen.consentGiven(today)).isTrue();

        // 생일을 2006 → 2016 처럼 바로잡아 만 14세 미만이 되면 동의가 필요하고, 동의 기록이 없으니 바로 막힌다
        LocalDate corrected = today.minusYears(10);
        family.editProfile(parentUserId, teen.getId(), new ProfileEdit("둘째", corrected, Sex.M), today);
        assertThat(teen.getDisplayName()).isEqualTo("둘째");
        assertThat(teen.getBirthDate()).isEqualTo(corrected);
        assertThat(teen.getSex()).isEqualTo(Sex.M);
        assertThat(teen.consentRequired(today)).isTrue();
        assertThat(teen.consentGiven(today)).isFalse();
        assertThat(teen.measurable(today)).isFalse();

        // 빠진 칸은 그대로
        family.editProfile(parentUserId, parent.getId(), new ProfileEdit(null, null, Sex.M), today);
        assertThat(parent.getDisplayName()).isEqualTo("부모");
        assertThat(parent.getBirthDate()).isEqualTo(LocalDate.of(1988, 3, 1));
        assertThat(parent.getSex()).isEqualTo(Sex.M);
    }

    @Test
    @DisplayName("프로필 고치기 규칙 — 보호자만 · 계정 붙은 남의 프로필 불가 · 미래 생일 불가 · PARENT 는 만 14세 이상 · 이름 공백 불가")
    void 프로필_고치기_규칙() {
        Family family = newFamily();
        Profile parent = family.getProfiles().getFirst();
        Profile child = addChild(family, "아이");
        Profile claimedChild = addChild(family, "둘째");
        UUID childUserId = UUID.randomUUID();
        claimedChild.claim(childUserId, consentedAt);
        Profile dadSeat = addParentSeat(family);
        ProfileEdit rename = new ProfileEdit("새 이름", null, null);

        assertThrows(
                FamilyAccessDeniedException.class,
                () -> family.editProfile(UUID.randomUUID(), child.getId(), rename, today));
        assertThrows(NotAParentException.class, () -> family.editProfile(childUserId, child.getId(), rename, today));
        assertThrows(
                NotAParentException.class, () -> family.editProfile(childUserId, claimedChild.getId(), rename, today));
        assertThrows(
                ProfileNotFoundException.class,
                () -> family.editProfile(parentUserId, UUID.randomUUID(), rename, today));
        assertThrows(
                NotOwnProfileException.class,
                () -> family.editProfile(parentUserId, claimedChild.getId(), rename, today));
        assertThrows(
                IllegalArgumentException.class,
                () -> family.editProfile(
                        parentUserId, child.getId(), new ProfileEdit(null, today.plusDays(1), null), today));
        assertThrows(
                Under14NotAllowedException.class,
                () -> family.editProfile(
                        parentUserId, dadSeat.getId(), new ProfileEdit(null, today.minusYears(13), null), today));
        assertThrows(
                Under14NotAllowedException.class,
                () -> family.editProfile(
                        parentUserId, parent.getId(), new ProfileEdit(null, today.minusYears(13), null), today));
        assertThrows(
                IllegalArgumentException.class,
                () -> family.editProfile(parentUserId, child.getId(), new ProfileEdit("  ", null, null), today));

        assertThat(claimedChild.getDisplayName()).isEqualTo("둘째");
        assertThat(dadSeat.getBirthDate()).isEqualTo(LocalDate.of(1986, 1, 1));
        assertThat(parent.getBirthDate()).isEqualTo(LocalDate.of(1988, 3, 1));
        assertThat(child.getDisplayName()).isEqualTo("아이");

        // 계정 없는 부모 자리는 고친다
        family.editProfile(parentUserId, dadSeat.getId(), rename, today);
        assertThat(dadSeat.getDisplayName()).isEqualTo("새 이름");
    }

    private Family newFamily() {
        return Family.createWithParent(parentUserId, "우리 가족", "부모", LocalDate.of(1988, 3, 1), Sex.F, today);
    }

    /** 아직 계정이 붙지 않은 부모 자리(초대 전 아빠). */
    private Profile addParentSeat(Family family) {
        return family.addMember(
                parentUserId,
                "아빠",
                LocalDate.of(1986, 1, 1),
                Sex.M,
                ProfileRole.PARENT,
                null,
                null,
                null,
                consentedAt,
                today);
    }

    private Profile addChild(Family family, String name) {
        return addChild(family, name, parentUserId);
    }

    private Profile addChild(Family family, String name, UUID actorUserId) {
        return family.addChild(actorUserId, name, childBirthDate, Sex.M, true, true, consentedAt, today);
    }
}
