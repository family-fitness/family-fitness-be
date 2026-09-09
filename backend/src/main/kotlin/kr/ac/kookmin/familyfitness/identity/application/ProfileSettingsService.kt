package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.domain.Family
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ConsentState(
    val consentGiven: Boolean,
    val consentAt: Instant?,
    val consentBy: UUID?,
    val measurable: Boolean,
)

/** 참여 수준(PARENT 본인 프로필)·보호자 동의(가족의 PARENT) 변경. */
@Service
@Transactional
class ProfileSettingsService(
    private val families: FamilyRepository,
    private val summaries: ProfileSummaries,
    private val clock: IdentityClock,
) {
    fun changeSupportMode(
        userId: UUID,
        profileId: UUID,
        mode: SupportMode,
    ): ProfileSummary {
        val family = load(profileId)
        val profile = family.changeSupportMode(userId, profileId, mode)
        families.save(family)
        return summaries.summary(profile)
    }

    fun updateConsent(
        userId: UUID,
        profileId: UUID,
        decision: GuardianConsent,
    ): ConsentState {
        val family = load(profileId)
        val profile = family.updateConsent(userId, profileId, decision, clock.now())
        families.save(family)
        val given = profile.consent.isGiven
        return ConsentState(
            consentGiven = profile.consentGiven(clock.today()),
            consentAt = if (given) profile.consent.personalAt else null,
            consentBy = if (given) profile.consent.byUserId else null,
            measurable = profile.measurable(clock.today()),
        )
    }

    private fun load(profileId: UUID): Family = families.findByProfileId(profileId) ?: throw ProfileNotFoundException(profileId)
}
