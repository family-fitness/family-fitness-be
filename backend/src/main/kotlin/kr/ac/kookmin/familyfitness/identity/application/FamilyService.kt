package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException
import kr.ac.kookmin.familyfitness.identity.domain.Family
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class CreatedFamily(
    val familyId: UUID,
    val familyName: String,
    val ownerProfile: ProfileSummary,
)

data class FamilyProfiles(
    val familyId: UUID,
    val familyName: String,
    val profiles: List<ProfileSummary>,
)

/** 가족 생성·구성원 추가·구성원 목록. 권한은 저장된 프로필로 판단한다. */
@Service
@Transactional
class FamilyService(
    private val families: FamilyRepository,
    private val summaries: ProfileSummaries,
    private val clock: IdentityClock,
) {
    /** 만든 사람은 항상 owner PARENT. 가족과 프로필 두 INSERT 는 한 트랜잭션. */
    fun createFamily(
        userId: UUID,
        familyName: String,
        ownerName: String,
        birthDate: LocalDate,
        sex: Sex,
    ): CreatedFamily {
        if (families.profilesOfUser(userId).isNotEmpty()) throw AlreadyInFamilyException()
        require(!birthDate.isAfter(clock.today())) { "생년월일은 미래일 수 없다" }
        val family = families.save(Family.createWithParent(userId, familyName, ownerName, birthDate, sex))
        return CreatedFamily(family.id, family.name, summaries.summary(family.profiles.single()))
    }

    fun addMember(
        userId: UUID,
        familyId: UUID,
        name: String,
        birthDate: LocalDate,
        sex: Sex,
        role: ProfileRole,
        guardianConsent: GuardianConsent?,
    ): ProfileSummary {
        val family = load(familyId)
        val profile =
            family.addMember(
                actorUserId = userId,
                displayName = name,
                birthDate = birthDate,
                sex = sex,
                role = role,
                guardianConsent = guardianConsent,
                consentedAt = clock.now(),
                today = clock.today(),
            )
        families.save(family)
        return summaries.summary(profile)
    }

    @Transactional(readOnly = true)
    fun profilesOf(
        userId: UUID,
        familyId: UUID,
    ): FamilyProfiles {
        val family = load(familyId)
        family.requireMember(userId)
        return FamilyProfiles(family.id, family.name, family.profiles.map(summaries::summary))
    }

    private fun load(familyId: UUID): Family = families.findById(familyId) ?: throw FamilyNotFoundException(familyId)
}
