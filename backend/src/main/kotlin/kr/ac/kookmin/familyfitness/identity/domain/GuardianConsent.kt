package kr.ac.kookmin.familyfitness.identity.domain

import kr.ac.kookmin.familyfitness.shared.domain.Ages
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 보호자 동의 판정 값 객체. 만 14세 미만이면 개인정보·건강정보 둘 다 true 여야 저장된다.
 * 서버가 동의를 자동으로 찍지 않는다 — 부모가 보낸 값을 그대로 판정만 한다.
 */
data class GuardianConsent(
    val personalData: Boolean,
    val healthData: Boolean,
) {
    val isComplete: Boolean get() = personalData && healthData

    companion object {
        fun isRequired(
            birthDate: LocalDate,
            today: LocalDate,
        ): Boolean = Ages.requiresGuardianConsent(birthDate, today)
    }
}

/**
 * 프로필에 기록된 동의. 철회해도 과거 동의 시각은 지우지 않고 [revokedAt] 만 채운다(감사 목적).
 * 유효한 동의 = 두 시각이 있고 철회되지 않음.
 */
data class ConsentRecord(
    val personalAt: Instant?,
    val healthAt: Instant?,
    val byUserId: UUID?,
    val revokedAt: Instant?,
) {
    val isGiven: Boolean get() = personalAt != null && healthAt != null && revokedAt == null

    fun revoke(at: Instant): ConsentRecord = copy(revokedAt = at)

    companion object {
        val NONE = ConsentRecord(null, null, null, null)

        fun granted(
            at: Instant,
            byUserId: UUID,
        ): ConsentRecord = ConsentRecord(at, at, byUserId, null)
    }
}
