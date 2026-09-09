package kr.ac.kookmin.familyfitness.identity.domain

import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 가족 애그리게잇 루트. 프로필의 생성·초대·동의·참여 수준 변경은 전부 여기를 거친다.
 * 불변식: PARENT 가 최소 한 명(만든 사람이 owner PARENT). 권한 판단은 HTTP 요청 값이 아니라 저장된 프로필로 한다.
 */
class Family private constructor(
    val id: UUID,
    val name: String,
    private val members: MutableList<Profile>,
) {
    init {
        require(name.isNotBlank()) { "가족 이름은 비어 있을 수 없다" }
    }

    val profiles: List<Profile> get() = members.toList()

    fun memberOf(userId: UUID): Profile? = members.firstOrNull { it.userId == userId }

    fun profileOrNull(profileId: UUID): Profile? = members.firstOrNull { it.id == profileId }

    fun profile(profileId: UUID): Profile = profileOrNull(profileId) ?: throw ProfileNotFoundException(profileId)

    /** 이 가족의 PARENT 계정이 이 가족의 프로필을 다룰 수 있다. */
    fun canManageProfile(
        userId: UUID,
        profileId: UUID,
    ): Boolean = memberOf(userId)?.isParent == true && profileOrNull(profileId) != null

    /** 구성원이 아니면 [FamilyAccessDeniedException], 구성원이지만 아이면 [NotAParentException]. */
    fun requireParent(userId: UUID): Profile {
        val actor = memberOf(userId) ?: throw FamilyAccessDeniedException()
        if (!actor.isParent) throw NotAParentException()
        return actor
    }

    fun requireMember(userId: UUID): Profile = memberOf(userId) ?: throw FamilyAccessDeniedException()

    /** 아이 프로필 추가 — [addMember] 의 CHILD 편의 메서드. */
    fun addChild(
        actorUserId: UUID,
        displayName: String,
        birthDate: LocalDate,
        sex: Sex,
        personalConsentGranted: Boolean,
        healthConsentGranted: Boolean,
        consentedAt: Instant,
        today: LocalDate,
    ): Profile =
        addMember(
            actorUserId = actorUserId,
            displayName = displayName,
            birthDate = birthDate,
            sex = sex,
            role = ProfileRole.CHILD,
            guardianConsent = GuardianConsent(personalConsentGranted, healthConsentGranted),
            consentedAt = consentedAt,
            today = today,
        )

    /**
     * 구성원 추가 — PARENT 만. 만 14세 미만이면 [guardianConsent] 가 둘 다 true 여야 하고,
     * 동의 시각·동의자는 서버(여기)가 채운다. 만 4세 미만도 프로필은 만든다(측정만 불가).
     */
    fun addMember(
        actorUserId: UUID,
        displayName: String,
        birthDate: LocalDate,
        sex: Sex,
        role: ProfileRole,
        guardianConsent: GuardianConsent?,
        consentedAt: Instant,
        today: LocalDate,
    ): Profile {
        requireParent(actorUserId)
        require(!birthDate.isAfter(today)) { "생년월일은 미래일 수 없다" }
        val consent =
            when {
                guardianConsent?.isComplete == true -> ConsentRecord.granted(consentedAt, actorUserId)
                GuardianConsent.isRequired(birthDate, today) -> throw GuardianConsentRequiredException()
                else -> ConsentRecord.NONE
            }
        val profile =
            Profile(
                id = UUID.randomUUID(),
                familyId = id,
                userId = null,
                role = role,
                isOwner = false,
                displayName = displayName,
                birthDate = birthDate,
                sex = sex,
                heightCm = null,
                weightKg = null,
                supportMode = null,
                claimCode = null,
                claimCodeClaimedAt = null,
                consent = consent,
            )
        members.add(profile)
        return profile
    }

    /** 초대 발급 — PARENT 만. 계정이 이미 붙은 프로필에는 발급하지 않는다. 재발급은 이전 코드를 즉시 무효화한다. */
    fun issueInvite(
        actorUserId: UUID,
        profileId: UUID,
        code: ClaimCode,
    ): Profile {
        requireParent(actorUserId)
        val profile = profile(profileId)
        profile.issueInvite(code)
        return profile
    }

    /**
     * 초대 코드 사용 전 규칙 검사. 실제 계정 연결은 조건부 UPDATE(동시성)로 저장소가 하므로 여기서는 상태를 바꾸지 않는다.
     * 순서: 코드 없음 → 이미 사용 → 만료 → 이미 이 가족 구성원.
     */
    fun prepareClaim(
        profileId: UUID,
        userId: UUID,
        now: Instant,
    ): Profile {
        val profile = profile(profileId)
        val code = profile.claimCode ?: throw ClaimCodeNotFoundException()
        if (profile.hasAccount) throw AlreadyClaimedException()
        if (code.isExpired(now)) throw ClaimCodeExpiredException()
        if (memberOf(userId) != null) throw AlreadyMemberException()
        return profile
    }

    /** 참여 수준 변경 — 본인 계정에 붙은 PARENT 프로필만. */
    fun changeSupportMode(
        actorUserId: UUID,
        profileId: UUID,
        mode: SupportMode,
    ): Profile {
        val profile = profile(profileId)
        if (profile.userId != actorUserId) throw NotOwnProfileException()
        profile.changeSupportMode(mode)
        return profile
    }

    /** 동의 변경 — 이 가족의 PARENT 만. */
    fun updateConsent(
        actorUserId: UUID,
        profileId: UUID,
        decision: GuardianConsent,
        at: Instant,
    ): Profile {
        requireParent(actorUserId)
        val profile = profile(profileId)
        profile.recordConsent(decision, actorUserId, at)
        return profile
    }

    /** 응원 규칙: 보내는 프로필은 내 계정의 것, 받는 프로필은 같은 가족의 다른 사람. */
    fun validateCheer(
        actorUserId: UUID,
        fromProfileId: UUID,
        toProfileId: UUID,
    ) {
        requireMember(actorUserId)
        val from = profileOrNull(fromProfileId)
        if (from == null || from.userId != actorUserId) throw NotOwnProfileException("fromProfileId 가 내 프로필이 아닙니다")
        if (fromProfileId == toProfileId) throw SelfCheerException()
        if (profileOrNull(toProfileId) == null) throw NotFamilyMemberException()
    }

    companion object {
        /** 가족 생성. 만든 사람은 항상 owner PARENT 이고 본인 계정에 바로 연결된다. */
        fun createWithParent(
            parentUserId: UUID,
            familyName: String,
            parentName: String,
            birthDate: LocalDate,
            sex: Sex,
        ): Family {
            val familyId = UUID.randomUUID()
            val owner =
                Profile(
                    id = UUID.randomUUID(),
                    familyId = familyId,
                    userId = parentUserId,
                    role = ProfileRole.PARENT,
                    isOwner = true,
                    displayName = parentName,
                    birthDate = birthDate,
                    sex = sex,
                    heightCm = null,
                    weightKg = null,
                    supportMode = null,
                    claimCode = null,
                    claimCodeClaimedAt = null,
                    consent = ConsentRecord.NONE,
                )
            return Family(familyId, familyName, mutableListOf(owner))
        }

        /** 저장소가 저장된 상태를 되살릴 때 쓴다. */
        fun restore(
            id: UUID,
            name: String,
            profiles: List<Profile>,
        ): Family {
            require(profiles.all { it.familyId == id }) { "다른 가족의 프로필이 섞여 있다" }
            return Family(id, name, profiles.toMutableList())
        }
    }
}
