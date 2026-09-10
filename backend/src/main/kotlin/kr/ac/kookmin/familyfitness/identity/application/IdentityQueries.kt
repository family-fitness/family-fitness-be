package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.domain.Family
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** 다른 모듈이 프로필을 읽는 통로([ProfileQuery]) 구현. */
@Service
@Transactional(readOnly = true)
class ProfileQueryService(
    private val families: FamilyRepository,
    private val summaries: ProfileSummaries,
) : ProfileQuery {
    override fun findSummary(profileId: UUID): ProfileSummary? =
        families.findByProfileId(profileId)?.profileOrNull(profileId)?.let(summaries::summary)

    override fun findDetails(profileId: UUID): ProfileDetails? =
        families.findByProfileId(profileId)?.profileOrNull(profileId)?.let(summaries::details)

    override fun summariesOfFamily(familyId: UUID): List<ProfileSummary> =
        families.findById(familyId)?.profiles?.map(summaries::summary) ?: emptyList()

    override fun detailsOfFamily(familyId: UUID): List<ProfileDetails> =
        families.findById(familyId)?.profiles?.map(summaries::details) ?: emptyList()

    override fun summariesOfUser(userId: UUID): List<ProfileSummary> = families.profilesOfUser(userId).map(summaries::summary)

    override fun familyName(familyId: UUID): String? = families.findById(familyId)?.name

    override fun allFamilyIds(): List<UUID> = families.allIds()
}

/** 가족 단위 권한 판단([FamilyAccess]) 구현. HTTP 요청의 role·familyId 를 믿지 않는다. */
@Service
@Transactional(readOnly = true)
class FamilyAccessService(
    private val families: FamilyRepository,
    private val summaries: ProfileSummaries,
) : FamilyAccess {
    override fun requireMember(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary = memberOf(family(familyId), userId)

    override fun requireParent(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary = parentOf(family(familyId), userId)

    override fun memberOf(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary? = families.findById(familyId)?.memberOf(userId)?.let(summaries::summary)

    override fun requireSameFamilyAsProfile(
        userId: UUID,
        profileId: UUID,
    ): ProfileSummary {
        val family = familyOfProfile(profileId)
        memberOf(family, userId)
        return summaries.summary(family.profile(profileId))
    }

    override fun requireParentOfProfile(
        userId: UUID,
        profileId: UUID,
    ): ProfileSummary = parentOf(familyOfProfile(profileId), userId)

    private fun family(familyId: UUID): Family = families.findById(familyId) ?: throw FamilyNotFoundException(familyId)

    private fun familyOfProfile(profileId: UUID): Family = families.findByProfileId(profileId) ?: throw ProfileNotFoundException(profileId)

    private fun memberOf(
        family: Family,
        userId: UUID,
    ): ProfileSummary = family.memberOf(userId)?.let(summaries::summary) ?: throw NotSameFamilyException()

    private fun parentOf(
        family: Family,
        userId: UUID,
    ): ProfileSummary {
        val member = memberOf(family, userId)
        if (!member.isParent) throw NotAParentException()
        return member
    }
}

/** 응원 집계([CheerQuery]) 구현. */
@Service
@Transactional(readOnly = true)
class CheerQueryService(
    private val cheers: CheerRepository,
) : CheerQuery {
    override fun countCheers(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Int = cheers.countInFamily(familyId, from, to)
}
