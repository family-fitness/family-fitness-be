package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyClaimedException
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeNotFoundException
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

data class Invitation(
    val claimCode: ClaimCode,
    val shareUrl: String,
)

data class ClaimResult(
    val profileId: UUID,
    val familyId: UUID,
    val role: ProfileRole,
    val nextStep: NextStep,
)

/** 초대 코드 발급·사용. 사용은 조건부 UPDATE 한 문장으로 동시 요청을 가른다. */
@Service
@Transactional
class InviteService(
    private val families: FamilyRepository,
    private val props: AppProperties,
    private val clock: IdentityClock,
) {
    private val random = SecureRandom()

    fun issueInvite(
        userId: UUID,
        profileId: UUID,
    ): Invitation {
        val family = families.findByProfileId(profileId) ?: throw ProfileNotFoundException(profileId)
        val code = freshCode()
        family.issueInvite(userId, profileId, code)
        families.save(family)
        return Invitation(code, shareUrlOf(code))
    }

    fun claim(
        userId: UUID,
        rawCode: String,
    ): ClaimResult {
        val code = ClaimCode.normalize(rawCode)
        val family = families.findByClaimCode(code) ?: throw ClaimCodeNotFoundException()
        val profile = family.profiles.firstOrNull { it.claimCode?.code == code } ?: throw ClaimCodeNotFoundException()
        val now = clock.now()
        family.prepareClaim(profile.id, userId, now)
        if (!families.attachUserIfUnclaimed(profile.id, userId, now)) throw AlreadyClaimedException("다른 계정이 먼저 사용했습니다")
        val nextStep = if (profile.role == ProfileRole.PARENT) NextStep.SUPPORT_MODE else NextStep.HOME
        return ClaimResult(profile.id, family.id, profile.role, nextStep)
    }

    private fun freshCode(): ClaimCode {
        val now = clock.now()
        repeat(MAX_GENERATE_ATTEMPTS) {
            val code = ClaimCode.generate(now, random)
            if (!families.isClaimCodeTaken(code.code)) return code
        }
        throw IllegalStateException("초대 코드를 만들지 못했습니다")
    }

    private fun shareUrlOf(code: ClaimCode): String = props.frontendBaseUrl.trimEnd('/') + "/claim?code=" + code.code

    companion object {
        private const val MAX_GENERATE_ATTEMPTS = 10
    }
}
