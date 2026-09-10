package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import java.time.LocalDate
import java.util.UUID

/*
 * 요청 본문. 필수 누락·형식 오류는 Bean Validation / Jackson 이 400 `BAD_REQUEST` 로 돌린다.
 * role·familyId 같은 권한 관련 값은 본문에서 읽더라도 신뢰하지 않는다 — 판단은 저장된 프로필로 한다.
 */

data class GoogleLoginRequest(
    @field:NotBlank val authorizationCode: String,
    @field:NotBlank val redirectUri: String,
    val claimCode: String? = null,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class DevLoginRequest(
    @field:NotBlank @field:Size(max = 191) val providerUserId: String,
    @field:Size(max = 255) val email: String? = null,
    val claimCode: String? = null,
)

data class OwnerRequest(
    @field:NotBlank @field:Size(min = 1, max = 20) val name: String,
    @field:PastOrPresent val birthDate: LocalDate,
    val sex: Sex,
)

data class CreateFamilyRequest(
    @field:NotBlank @field:Size(min = 1, max = 20) val familyName: String,
    @field:Valid val owner: OwnerRequest,
)

data class GuardianConsentRequest(
    @field:NotNull val personalData: Boolean?,
    @field:NotNull val healthData: Boolean?,
) {
    fun toDomain() = GuardianConsent(personalData == true, healthData == true)
}

data class AddMemberRequest(
    @field:NotBlank @field:Size(min = 1, max = 20) val name: String,
    @field:PastOrPresent val birthDate: LocalDate,
    val sex: Sex,
    val role: ProfileRole,
    @field:Valid val guardianConsent: GuardianConsentRequest? = null,
)

data class ClaimRequest(
    @field:NotBlank @field:Size(max = 20) val claimCode: String,
)

data class SupportModeRequest(
    val supportMode: SupportMode,
)

data class ConsentRequest(
    @field:NotNull val personalData: Boolean?,
    @field:NotNull val healthData: Boolean?,
) {
    fun toDomain() = GuardianConsent(personalData == true, healthData == true)
}

data class CheerRequest(
    val fromProfileId: UUID,
    val toProfileId: UUID,
    @field:Size(max = 100) val message: String? = null,
    @field:Size(max = 20) val emoji: String? = null,
    val missionId: UUID? = null,
) {
    /** message/emoji 중 최소 하나. */
    @get:AssertTrue(message = "message 나 emoji 중 하나는 있어야 합니다")
    val isContentPresent: Boolean get() = !message.isNullOrBlank() || !emoji.isNullOrBlank()
}
