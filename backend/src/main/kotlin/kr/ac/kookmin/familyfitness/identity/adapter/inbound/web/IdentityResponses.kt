package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web

import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.AuthResult
import kr.ac.kookmin.familyfitness.identity.application.AuthSession
import kr.ac.kookmin.familyfitness.identity.application.NextStep
import kr.ac.kookmin.familyfitness.identity.domain.Cheer
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import java.time.Instant
import java.util.UUID

// 응답 본문. 계약(api-contract §1)의 필드 이름을 그대로 쓴다. ProfileSummary 는 공개 언어 그대로 내보낸다.

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID,
    val nextStep: NextStep,
    val profiles: List<ProfileSummary>,
) {
    companion object {
        fun of(result: AuthResult) =
            AuthResponse(
                accessToken = result.tokens.accessToken,
                refreshToken = result.tokens.refreshToken,
                userId = result.session.userId,
                nextStep = result.session.nextStep,
                profiles = result.session.profiles,
            )
    }
}

data class MeResponse(
    val userId: UUID,
    val nextStep: NextStep,
    val profiles: List<ProfileSummary>,
) {
    companion object {
        fun of(session: AuthSession) = MeResponse(session.userId, session.nextStep, session.profiles)
    }
}

data class FamilyCreatedResponse(
    val familyId: UUID,
    val familyName: String,
    val ownerProfile: ProfileSummary,
)

data class FamilyProfilesResponse(
    val familyId: UUID,
    val familyName: String,
    val profiles: List<ProfileSummary>,
)

data class InviteResponse(
    val claimCode: String,
    val expiresAt: Instant,
    val shareUrl: String,
)

data class ClaimResponse(
    val profileId: UUID,
    val familyId: UUID,
    val role: ProfileRole,
    val nextStep: NextStep,
)

data class ConsentResponse(
    val consentGiven: Boolean,
    val consentAt: Instant?,
    val consentBy: UUID?,
    val measurable: Boolean,
)

data class CheerResponse(
    val cheerId: UUID,
    val fromProfileId: UUID,
    val toProfileId: UUID,
    val message: String?,
    val emoji: String?,
    val missionId: UUID?,
    val createdAt: Instant,
) {
    companion object {
        fun of(cheer: Cheer) =
            CheerResponse(
                cheerId = cheer.id,
                fromProfileId = cheer.fromProfileId,
                toProfileId = cheer.toProfileId,
                message = cheer.message,
                emoji = cheer.emoji,
                missionId = cheer.missionId,
                createdAt = cheer.createdAt,
            )
    }
}
