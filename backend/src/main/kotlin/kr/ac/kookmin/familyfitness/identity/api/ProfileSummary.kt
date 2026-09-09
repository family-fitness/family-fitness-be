package kr.ac.kookmin.familyfitness.identity.api

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** 초대코드 상태. */
enum class InviteStatus {
    NONE,
    ISSUED,
    EXPIRED,
    CLAIMED,
}

/**
 * identity 가 밖으로 내보내는 유일한 공개 언어(Published Language).
 * 다른 모듈은 Profile 엔티티를 받지 않고 이 요약만 본다. API 응답에도 이 모양이 그대로 나간다.
 */
data class ProfileSummary(
    val profileId: UUID,
    val familyId: UUID,
    val name: String,
    val role: ProfileRole,
    val ageGroup: AgeGroup,
    val hasAccount: Boolean,
    val inviteStatus: InviteStatus,
    val supportMode: SupportMode?,
    /** 만 4세 이상이고 (동의 불필요이거나) 동의가 살아 있는가 */
    val measurable: Boolean,
    /** 만 14세 미만인가 */
    val consentRequired: Boolean,
    /** 동의가 살아 있는가. 동의 불필요(성인)면 true */
    val consentGiven: Boolean,
) {
    val isParent: Boolean get() = role == ProfileRole.PARENT
}

/**
 * 측정·편성 계산에 필요한 내부용 상세. AI 서비스로는 이 값을 그대로 보내지 않고
 * 나이·성별·신장·체중만 [kr.ac.kookmin.familyfitness.shared.domain.ProfileRef] 와 함께 보낸다.
 */
data class ProfileDetails(
    val profileId: UUID,
    val familyId: UUID,
    val userId: UUID?,
    val name: String,
    val role: ProfileRole,
    val birthDate: LocalDate,
    val sex: Sex,
    val heightCm: BigDecimal?,
    val weightKg: BigDecimal?,
    val supportMode: SupportMode?,
    val consentGiven: Boolean,
)
