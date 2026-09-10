package kr.ac.kookmin.familyfitness.identity.api

import java.time.Instant
import java.util.UUID

/** 다른 모듈이 프로필 정보를 읽는 유일한 통로. */
interface ProfileQuery {
    fun findSummary(profileId: UUID): ProfileSummary?

    fun findDetails(profileId: UUID): ProfileDetails?

    fun summariesOfFamily(familyId: UUID): List<ProfileSummary>

    fun detailsOfFamily(familyId: UUID): List<ProfileDetails>

    /** 이 계정에 붙은 프로필들(여러 가족 가능). */
    fun summariesOfUser(userId: UUID): List<ProfileSummary>

    fun familyName(familyId: UUID): String?

    /** 전 가족 ID. 주간 코치 스케줄러가 가족 단위로 돈다. */
    fun allFamilyIds(): List<UUID>
}

/**
 * 가족 단위 권한 판단. HTTP 요청의 role·familyId 를 믿지 않고 identity 저장소에서 판단한다.
 * 실패 시 [NotSameFamilyException] / [NotAParentException] 을 던진다.
 */
interface FamilyAccess {
    /** 계정이 이 가족의 구성원인지. 구성원이면 그 계정의 프로필 요약을 돌려준다. */
    fun requireMember(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary

    /** 계정이 이 가족의 PARENT 인지. 부모 프로필 요약을 돌려준다. */
    fun requireParent(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary

    fun memberOf(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary?

    /** 대상 프로필과 같은 가족의 구성원인지. 대상 프로필 요약을 돌려준다. 없으면 [ProfileNotFoundException]. */
    fun requireSameFamilyAsProfile(
        userId: UUID,
        profileId: UUID,
    ): ProfileSummary

    /** 대상 프로필이 속한 가족의 PARENT 인지. 호출자(부모) 프로필 요약을 돌려준다. */
    fun requireParentOfProfile(
        userId: UUID,
        profileId: UUID,
    ): ProfileSummary
}

/** 응원 집계(주간 요약용). */
interface CheerQuery {
    fun countCheers(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Int
}
