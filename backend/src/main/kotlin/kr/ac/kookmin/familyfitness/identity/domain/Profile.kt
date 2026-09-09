package kr.ac.kookmin.familyfitness.identity.domain

import kr.ac.kookmin.familyfitness.identity.api.InviteStatus
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Ages
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 가족 구성원. 로그인 계정([userId])과 다르다 — 아이는 계정 없이 부모가 대리 관리한다.
 * 역할은 생성 시 확정되고 초대받는 쪽이 못 고친다. 상태 변경은 애그리게잇 루트([Family])를 통해서만 한다.
 */
class Profile(
    val id: UUID,
    val familyId: UUID,
    userId: UUID?,
    val role: ProfileRole,
    val isOwner: Boolean,
    val displayName: String,
    val birthDate: LocalDate,
    val sex: Sex,
    val heightCm: BigDecimal?,
    val weightKg: BigDecimal?,
    supportMode: SupportMode?,
    claimCode: ClaimCode?,
    claimCodeClaimedAt: Instant?,
    consent: ConsentRecord,
) {
    init {
        require(displayName.isNotBlank()) { "이름은 비어 있을 수 없다" }
    }

    var userId: UUID? = userId
        private set

    var supportMode: SupportMode? = supportMode
        private set

    var claimCode: ClaimCode? = claimCode
        private set

    var claimCodeClaimedAt: Instant? = claimCodeClaimedAt
        private set

    var consent: ConsentRecord = consent
        private set

    val isParent: Boolean get() = role == ProfileRole.PARENT
    val hasAccount: Boolean get() = userId != null

    fun ageGroup(today: LocalDate): AgeGroup = AgeGroup.of(birthDate, today)

    /** 만 14세 미만인가 */
    fun consentRequired(today: LocalDate): Boolean = GuardianConsent.isRequired(birthDate, today)

    /** 동의가 살아 있는가. 동의 불필요(만 14세 이상)면 true */
    fun consentGiven(today: LocalDate): Boolean = !consentRequired(today) || consent.isGiven

    /** 만 4세 이상이고 (동의 불필요이거나) 동의가 살아 있는가 */
    fun measurable(today: LocalDate): Boolean = Ages.isMeasurable(birthDate, today) && consentGiven(today)

    fun inviteStatus(now: Instant): InviteStatus {
        val code = claimCode ?: return InviteStatus.NONE
        return when {
            claimCodeClaimedAt != null -> InviteStatus.CLAIMED
            code.isExpired(now) -> InviteStatus.EXPIRED
            else -> InviteStatus.ISSUED
        }
    }

    internal fun issueInvite(code: ClaimCode) {
        if (hasAccount) throw AlreadyClaimedException()
        claimCode = code
        claimCodeClaimedAt = null
    }

    /** 계정을 붙인다. 코드는 CLAIMED 상태를 파생하기 위해 남겨 둔다. */
    fun claim(
        userId: UUID,
        at: Instant,
    ) {
        if (hasAccount) throw AlreadyClaimedException()
        this.userId = userId
        this.claimCodeClaimedAt = at
    }

    internal fun changeSupportMode(mode: SupportMode) {
        if (!isParent) throw SupportModeNotApplicableException()
        supportMode = mode
    }

    /** 둘 다 true 면 새로 동의(시각·동의자 갱신), 하나라도 false 면 철회(과거 시각은 유지). */
    internal fun recordConsent(
        decision: GuardianConsent,
        byUserId: UUID,
        at: Instant,
    ) {
        consent =
            if (decision.isComplete) {
                ConsentRecord.granted(at, byUserId)
            } else {
                consent.revoke(at)
            }
    }
}
