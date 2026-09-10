package kr.ac.kookmin.familyfitness.identity.domain

import kr.ac.kookmin.familyfitness.identity.api.InviteStatus
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * v2 테스트 우선 계약: 부모 계정 하나로 여러 아이를 관리한다.
 * HTTP 인증과 DB 원자성은 후속 통합 테스트로 검증한다.
 * 계약(api-contract §1)에 따라 생년월일·성별은 모든 프로필의 필수값이다.
 */
class FamilyTest {
    private val parentUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val consentedAt = Instant.parse("2026-09-08T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 8)
    private val childBirthDate = LocalDate.of(2018, 5, 20)

    @Test
    fun `부모가 가족을 만들면 본인 계정과 연결된 부모 프로필이 생긴다`() {
        val family = newFamily()

        val parent = family.profiles.single()
        assertThat(parent.userId).isEqualTo(parentUserId)
        assertThat(parent.role).isEqualTo(ProfileRole.PARENT)
        assertThat(parent.isOwner).isTrue()
        assertThat(parent.familyId).isEqualTo(family.id)
        assertThat(parent.sex).isEqualTo(Sex.F)
        assertThat(family.name).isEqualTo("우리 가족")
    }

    @Test
    fun `아이 계정과 연락처 및 신체 측정 없이 아이 프로필을 추가한다`() {
        val family = newFamily()

        val child = addChild(family, "첫째")

        assertThat(child.userId).isNull()
        assertThat(child.displayName).isEqualTo("첫째")
        assertThat(child.birthDate).isEqualTo(childBirthDate)
        assertThat(child.sex).isEqualTo(Sex.M)
        assertThat(child.role).isEqualTo(ProfileRole.CHILD)
        assertThat(child.isOwner).isFalse()
        assertThat(child.heightCm).isNull()
        assertThat(child.weightKg).isNull()
        assertThat(child.supportMode).isNull()
        assertThat(family.profiles.map { it.id }).contains(child.id)
    }

    @Test
    fun `한 부모 계정으로 두 아이를 서로 다른 프로필로 관리한다`() {
        val family = newFamily()

        val first = addChild(family, "첫째")
        val second = addChild(family, "둘째")

        assertThat(first.id).isNotEqualTo(second.id)
        assertThat(family.profiles).hasSize(3)
        assertThat(first.userId).isNull()
        assertThat(second.userId).isNull()
        assertThat(family.canManageProfile(parentUserId, first.id)).isTrue()
        assertThat(family.canManageProfile(parentUserId, second.id)).isTrue()
    }

    @Test
    fun `초대를 발급하지 않아도 부모가 아이 프로필을 관리할 수 있다`() {
        val family = newFamily()
        val child = addChild(family, "아이")

        assertThat(family.canManageProfile(parentUserId, child.id)).isTrue()
        assertThat(child.userId).isNull()
        assertThat(family.profiles.single { it.userId == parentUserId }.role)
            .isEqualTo(ProfileRole.PARENT)
    }

    @Test
    fun `다른 가족에서 부모인 계정도 이 가족의 아이를 관리할 수 없다`() {
        val family = newFamily()
        val child = addChild(family, "아이")
        val otherParentId = UUID.randomUUID()
        val otherFamily = Family.createWithParent(otherParentId, "다른 가족", "다른 부모", LocalDate.of(1985, 1, 1), Sex.M)

        assertThat(otherFamily.profiles.single().role).isEqualTo(ProfileRole.PARENT)
        assertThat(family.canManageProfile(otherParentId, child.id)).isFalse()
        assertThrows<FamilyAccessDeniedException> {
            addChild(family, "추가 시도", actorUserId = otherParentId)
        }
        assertThat(family.profiles).hasSize(2)
    }

    @Test
    fun `같은 부모라도 현재 가족에 속하지 않은 프로필을 관리할 수 없다`() {
        val family = newFamily()

        assertThat(family.canManageProfile(parentUserId, UUID.randomUUID())).isFalse()
    }

    @Test
    fun `필요한 동의 없이 아이 프로필을 추가하지 않는다`() {
        val family = newFamily()

        assertThrows<GuardianConsentRequiredException> {
            family.addChild(
                actorUserId = parentUserId,
                displayName = "아이",
                birthDate = childBirthDate,
                sex = Sex.M,
                personalConsentGranted = false,
                healthConsentGranted = false,
                consentedAt = consentedAt,
                today = today,
            )
        }
        assertThrows<GuardianConsentRequiredException> {
            family.addMember(
                actorUserId = parentUserId,
                displayName = "아이",
                birthDate = childBirthDate,
                sex = Sex.M,
                role = ProfileRole.CHILD,
                guardianConsent = GuardianConsent(personalData = true, healthData = false),
                consentedAt = consentedAt,
                today = today,
            )
        }
        assertThrows<GuardianConsentRequiredException> {
            family.addMember(
                actorUserId = parentUserId,
                displayName = "아이",
                birthDate = childBirthDate,
                sex = Sex.M,
                role = ProfileRole.CHILD,
                guardianConsent = null,
                consentedAt = consentedAt,
                today = today,
            )
        }

        assertThat(family.profiles).hasSize(1)
    }

    @Test
    fun `만 14세 이상 구성원은 동의 없이 추가되고 동의 상태는 참으로 본다`() {
        val family = newFamily()

        val teen =
            family.addMember(
                actorUserId = parentUserId,
                displayName = "큰애",
                birthDate = today.minusYears(14),
                sex = Sex.F,
                role = ProfileRole.CHILD,
                guardianConsent = null,
                consentedAt = consentedAt,
                today = today,
            )

        assertThat(teen.consentRequired(today)).isFalse()
        assertThat(teen.consentGiven(today)).isTrue()
        assertThat(teen.measurable(today)).isTrue()
        assertThat(teen.consent.isGiven).isFalse()
    }

    @Test
    fun `동의가 있는 아이는 측정 가능하고 서버가 동의 시각과 동의자를 채운다`() {
        val family = newFamily()

        val child = addChild(family, "아이")

        assertThat(child.consentRequired(today)).isTrue()
        assertThat(child.consentGiven(today)).isTrue()
        assertThat(child.measurable(today)).isTrue()
        assertThat(child.consent.personalAt).isEqualTo(consentedAt)
        assertThat(child.consent.healthAt).isEqualTo(consentedAt)
        assertThat(child.consent.byUserId).isEqualTo(parentUserId)
        assertThat(child.consent.revokedAt).isNull()
    }

    @Test
    fun `만 4세 미만도 프로필은 만들어지지만 측정 대상은 아니다`() {
        val family = newFamily()

        val toddler =
            family.addChild(
                actorUserId = parentUserId,
                displayName = "막내",
                birthDate = today.minusYears(2),
                sex = Sex.M,
                personalConsentGranted = true,
                healthConsentGranted = true,
                consentedAt = consentedAt,
                today = today,
            )

        assertThat(toddler.consentGiven(today)).isTrue()
        assertThat(toddler.measurable(today)).isFalse()
    }

    @Test
    fun `아이 계정은 구성원을 추가할 수 없다`() {
        val family = newFamily()
        val child = addChild(family, "아이")
        val childUserId = UUID.randomUUID()
        child.claim(childUserId, consentedAt)

        assertThrows<NotAParentException> {
            addChild(family, "동생", actorUserId = childUserId)
        }
        assertThat(family.canManageProfile(childUserId, child.id)).isFalse()
    }

    @Test
    fun `동의를 철회하면 측정 불가가 되고 과거 동의 시각은 남는다`() {
        val family = newFamily()
        val child = addChild(family, "아이")
        val revokedAt = consentedAt.plusSeconds(60)

        family.updateConsent(parentUserId, child.id, GuardianConsent(personalData = true, healthData = false), revokedAt)

        assertThat(child.consent.isGiven).isFalse()
        assertThat(child.consent.revokedAt).isEqualTo(revokedAt)
        assertThat(child.consent.personalAt).isEqualTo(consentedAt)
        assertThat(child.consentGiven(today)).isFalse()
        assertThat(child.measurable(today)).isFalse()

        val regrantedAt = revokedAt.plusSeconds(60)
        family.updateConsent(parentUserId, child.id, GuardianConsent(personalData = true, healthData = true), regrantedAt)

        assertThat(child.consent.isGiven).isTrue()
        assertThat(child.consent.personalAt).isEqualTo(regrantedAt)
        assertThat(child.consent.revokedAt).isNull()
        assertThat(child.measurable(today)).isTrue()
    }

    @Test
    fun `동의 변경은 이 가족의 부모만 할 수 있다`() {
        val family = newFamily()
        val child = addChild(family, "아이")

        assertThrows<FamilyAccessDeniedException> {
            family.updateConsent(UUID.randomUUID(), child.id, GuardianConsent(true, true), consentedAt)
        }
        assertThrows<ProfileNotFoundException> {
            family.updateConsent(parentUserId, UUID.randomUUID(), GuardianConsent(true, true), consentedAt)
        }
    }

    @Test
    fun `초대 상태는 코드 유무와 만료 및 사용 여부에서 파생된다`() {
        val family = newFamily()
        val child = addChild(family, "아이")
        val now = consentedAt
        val code = ClaimCode("ABC234", now.plus(Duration.ofDays(7)))

        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.NONE)

        family.issueInvite(parentUserId, child.id, code)
        assertThat(child.claimCode).isEqualTo(code)
        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.ISSUED)
        assertThat(child.inviteStatus(code.expiresAt)).isEqualTo(InviteStatus.EXPIRED)

        child.claim(UUID.randomUUID(), now.plusSeconds(10))
        assertThat(child.inviteStatus(now)).isEqualTo(InviteStatus.CLAIMED)
        assertThat(child.inviteStatus(code.expiresAt.plusSeconds(1))).isEqualTo(InviteStatus.CLAIMED)
        assertThat(child.hasAccount).isTrue()
    }

    @Test
    fun `초대 재발급은 이전 코드를 즉시 무효로 만들고 계정이 붙은 프로필에는 발급하지 않는다`() {
        val family = newFamily()
        val child = addChild(family, "아이")
        val first = ClaimCode("ABC234", consentedAt.plus(Duration.ofDays(7)))
        val second = ClaimCode("XYZ789", consentedAt.plus(Duration.ofDays(8)))

        family.issueInvite(parentUserId, child.id, first)
        family.issueInvite(parentUserId, child.id, second)
        assertThat(child.claimCode).isEqualTo(second)

        assertThrows<AlreadyClaimedException> {
            family.issueInvite(parentUserId, family.profiles.single { it.isOwner }.id, first)
        }
        assertThrows<FamilyAccessDeniedException> {
            family.issueInvite(UUID.randomUUID(), child.id, first)
        }
    }

    @Test
    fun `초대 코드 사용 규칙 - 만료·이미 사용·이미 구성원`() {
        val family = newFamily()
        val child = addChild(family, "아이")
        val now = consentedAt
        val code = ClaimCode("ABC234", now.plus(Duration.ofDays(7)))
        val newcomer = UUID.randomUUID()

        assertThrows<ClaimCodeNotFoundException> { family.prepareClaim(child.id, newcomer, now) }

        family.issueInvite(parentUserId, child.id, code)
        assertThat(family.prepareClaim(child.id, newcomer, now)).isSameAs(child)
        assertThrows<ClaimCodeExpiredException> { family.prepareClaim(child.id, newcomer, code.expiresAt) }
        assertThrows<AlreadyMemberException> { family.prepareClaim(child.id, parentUserId, now) }

        child.claim(newcomer, now)
        assertThrows<AlreadyClaimedException> { family.prepareClaim(child.id, UUID.randomUUID(), now) }
    }

    @Test
    fun `참여 수준은 본인 부모 프로필에만 바꿀 수 있다`() {
        val family = newFamily()
        val parent = family.profiles.single()
        val child = addChild(family, "아이")

        family.changeSupportMode(parentUserId, parent.id, SupportMode.WEEKEND)
        assertThat(parent.supportMode).isEqualTo(SupportMode.WEEKEND)

        assertThrows<NotOwnProfileException> {
            family.changeSupportMode(parentUserId, child.id, SupportMode.FULL)
        }
        assertThrows<NotOwnProfileException> {
            family.changeSupportMode(UUID.randomUUID(), parent.id, SupportMode.FULL)
        }

        val childUserId = UUID.randomUUID()
        child.claim(childUserId, consentedAt)
        assertThrows<SupportModeNotApplicableException> {
            family.changeSupportMode(childUserId, child.id, SupportMode.FULL)
        }
    }

    @Test
    fun `응원은 내 프로필에서 같은 가족의 다른 프로필로만 보낸다`() {
        val family = newFamily()
        val parent = family.profiles.single()
        val child = addChild(family, "아이")
        val stranger = UUID.randomUUID()

        family.validateCheer(parentUserId, parent.id, child.id)

        assertThrows<FamilyAccessDeniedException> { family.validateCheer(stranger, parent.id, child.id) }
        assertThrows<NotOwnProfileException> { family.validateCheer(parentUserId, child.id, parent.id) }
        assertThrows<SelfCheerException> { family.validateCheer(parentUserId, parent.id, parent.id) }
        assertThrows<NotFamilyMemberException> { family.validateCheer(parentUserId, parent.id, UUID.randomUUID()) }
    }

    private fun newFamily() = Family.createWithParent(parentUserId, "우리 가족", "부모", LocalDate.of(1988, 3, 1), Sex.F)

    private fun addChild(
        family: Family,
        name: String,
        actorUserId: UUID = parentUserId,
    ) = family.addChild(
        actorUserId = actorUserId,
        displayName = name,
        birthDate = childBirthDate,
        sex = Sex.M,
        personalConsentGranted = true,
        healthConsentGranted = true,
        consentedAt = consentedAt,
        today = today,
    )
}
