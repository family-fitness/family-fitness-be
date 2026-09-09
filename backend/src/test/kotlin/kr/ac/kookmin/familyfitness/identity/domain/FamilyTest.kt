package kr.ac.kookmin.familyfitness.identity.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * v2 테스트 우선 계약: 부모 계정 하나로 여러 아이를 관리한다.
 * Family와 관련 운영 타입은 구현 전이다. HTTP 인증과 DB 원자성은 후속 통합 테스트로 검증한다.
 */
class FamilyTest {
    private val parentUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val consentedAt = Instant.parse("2026-09-08T10:00:00Z")

    @Test
    fun `부모가 가족을 만들면 본인 계정과 연결된 부모 프로필이 생긴다`() {
        val family = newFamily()

        val parent = family.profiles.single()
        assertThat(parent.userId).isEqualTo(parentUserId)
        assertThat(parent.role).isEqualTo(ProfileRole.PARENT)
        assertThat(parent.isOwner).isTrue()
    }

    @Test
    fun `아이 계정과 연락처 및 신체 측정 없이 아이 프로필을 추가한다`() {
        val family = newFamily()

        val child = addChild(family, "첫째")

        assertThat(child.userId).isNull()
        assertThat(child.displayName).isEqualTo("첫째")
        assertThat(child.birthDate).isEqualTo(LocalDate.of(2018, 5, 20))
        assertThat(child.role).isEqualTo(ProfileRole.CHILD)
        assertThat(child.isOwner).isFalse()
        assertThat(child.sex).isNull()
        assertThat(child.heightCm).isNull()
        assertThat(child.weightKg).isNull()
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
        val otherFamily = Family.createWithParent(otherParentId, "다른 가족", "다른 부모")

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
                birthDate = LocalDate.of(2018, 5, 20),
                personalConsentGranted = false,
                healthConsentGranted = false,
                consentedAt = consentedAt,
            )
        }

        assertThat(family.profiles).hasSize(1)
    }

    private fun newFamily() = Family.createWithParent(parentUserId, "우리 가족", "부모")

    private fun addChild(
        family: Family,
        name: String,
        actorUserId: UUID = parentUserId,
    ) = family.addChild(
        actorUserId = actorUserId,
        displayName = name,
        birthDate = LocalDate.of(2018, 5, 20),
        personalConsentGranted = true,
        healthConsentGranted = true,
        consentedAt = consentedAt,
    )
}
