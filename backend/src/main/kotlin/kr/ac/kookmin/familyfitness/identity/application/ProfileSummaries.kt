package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.domain.Profile
import org.springframework.stereotype.Component

/** 도메인 [Profile] → 공개 언어([ProfileSummary]·[ProfileDetails]). 나이·만료 판정은 현재 시각 기준. */
@Component
class ProfileSummaries(
    private val clock: IdentityClock,
) {
    fun summary(profile: Profile): ProfileSummary {
        val today = clock.today()
        return ProfileSummary(
            profileId = profile.id,
            familyId = profile.familyId,
            name = profile.displayName,
            role = profile.role,
            ageGroup = profile.ageGroup(today),
            hasAccount = profile.hasAccount,
            inviteStatus = profile.inviteStatus(clock.now()),
            supportMode = profile.supportMode,
            measurable = profile.measurable(today),
            consentRequired = profile.consentRequired(today),
            consentGiven = profile.consentGiven(today),
        )
    }

    fun details(profile: Profile): ProfileDetails =
        ProfileDetails(
            profileId = profile.id,
            familyId = profile.familyId,
            userId = profile.userId,
            name = profile.displayName,
            role = profile.role,
            birthDate = profile.birthDate,
            sex = profile.sex,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            supportMode = profile.supportMode,
            consentGiven = profile.consentGiven(clock.today()),
        )
}
