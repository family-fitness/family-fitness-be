package kr.ac.kookmin.familyfitness.coaching.support

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity
import kr.ac.kookmin.familyfitness.coaching.application.AppTime
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 테스트 기준 시각: 2026-09-09(수) 10:00 KST. 이번 주 월요일은 2026-09-07. */
object Fixed {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    val NOW: Instant = Instant.parse("2026-09-09T01:00:00Z")
    val TODAY: LocalDate = LocalDate.of(2026, 9, 9)
    val WEEK_START: LocalDate = LocalDate.of(2026, 9, 7)

    fun time(now: Instant = NOW): AppTime = AppTime(Clock.fixed(now, ZONE), ZONE)
}

/** 한 가족: 부모(계정 있음) · 아이(계정 있음, 11세) · 응원만 하는 부모. */
class Family(
    val familyId: UUID = UUID.randomUUID(),
) {
    val parentUser: UUID = UUID.randomUUID()
    val childUser: UUID = UUID.randomUUID()
    val cheerParentUser: UUID = UUID.randomUUID()
    val outsiderUser: UUID = UUID.randomUUID()

    val parent = details("엄마", ProfileRole.PARENT, LocalDate.of(1985, 3, 1), Sex.F, parentUser, SupportMode.FULL)
    val child = details("민준", ProfileRole.CHILD, LocalDate.of(2015, 5, 20), Sex.M, childUser, null)
    val cheerParent = details("아빠", ProfileRole.PARENT, LocalDate.of(1983, 7, 7), Sex.M, cheerParentUser, SupportMode.CHEER_ONLY)

    val members: List<ProfileDetails> get() = listOf(parent, child, cheerParent)

    private fun details(
        name: String,
        role: ProfileRole,
        birthDate: LocalDate,
        sex: Sex,
        userId: UUID?,
        supportMode: SupportMode?,
    ) = ProfileDetails(
        profileId = UUID.randomUUID(),
        familyId = familyId,
        userId = userId,
        name = name,
        role = role,
        birthDate = birthDate,
        sex = sex,
        heightCm = null,
        weightKg = null,
        supportMode = supportMode,
        consentGiven = true,
    )
}

fun ProfileDetails.summary(on: LocalDate = Fixed.TODAY): ProfileSummary =
    ProfileSummary(
        profileId = profileId,
        familyId = familyId,
        name = name,
        role = role,
        ageGroup = AgeGroup.of(birthDate, on),
        hasAccount = userId != null,
        inviteStatus = if (userId != null) InviteStatus.CLAIMED else InviteStatus.NONE,
        supportMode = supportMode,
        measurable = true,
        consentRequired = false,
        consentGiven = consentGiven,
    )

/** identity 공개 API 의 결정적 가짜. 여러 가족을 등록할 수 있다. */
class FakeIdentity(
    vararg families: Family,
) : ProfileQuery,
    FamilyAccess,
    CheerQuery {
    val families = families.toMutableList()
    var cheerCount = 0

    private val all: List<ProfileDetails> get() = families.flatMap { it.members }

    override fun findSummary(profileId: UUID): ProfileSummary? = findDetails(profileId)?.summary()

    override fun findDetails(profileId: UUID): ProfileDetails? = all.firstOrNull { it.profileId == profileId }

    override fun summariesOfFamily(familyId: UUID): List<ProfileSummary> = detailsOfFamily(familyId).map { it.summary() }

    override fun detailsOfFamily(familyId: UUID): List<ProfileDetails> = all.filter { it.familyId == familyId }

    override fun summariesOfUser(userId: UUID): List<ProfileSummary> = all.filter { it.userId == userId }.map { it.summary() }

    override fun familyName(familyId: UUID): String? = families.firstOrNull { it.familyId == familyId }?.let { "가족" }

    override fun requireMember(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary = memberOf(userId, familyId) ?: throw NotSameFamilyException()

    override fun requireParent(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary = requireMember(userId, familyId).also { if (!it.isParent) throw NotAParentException() }

    override fun memberOf(
        userId: UUID,
        familyId: UUID,
    ): ProfileSummary? = all.firstOrNull { it.userId == userId && it.familyId == familyId }?.summary()

    override fun requireSameFamilyAsProfile(
        userId: UUID,
        profileId: UUID,
    ): ProfileSummary {
        val target = findDetails(profileId) ?: throw ProfileNotFoundException(profileId)
        requireMember(userId, target.familyId)
        return target.summary()
    }

    override fun requireParentOfProfile(
        userId: UUID,
        profileId: UUID,
    ): ProfileSummary {
        val target = findDetails(profileId) ?: throw ProfileNotFoundException(profileId)
        return requireParent(userId, target.familyId)
    }

    override fun countCheers(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Int = cheerCount
}

class FakeFitness : FitnessQuery {
    val latest = mutableMapOf<UUID, LatestFitness>()

    fun measured(
        profileId: UUID,
        vararg items: Pair<String, Double>,
    ) {
        latest[profileId] =
            LatestFitness(
                profileId = profileId,
                fitnessTestId = UUID.randomUUID(),
                testedOn = Fixed.TODAY.minusDays(3),
                heightCm = BigDecimal("140.5"),
                weightKg = BigDecimal("35.0"),
                measurements = items.associate { it.first to BigDecimal.valueOf(it.second) },
                weakest = null,
                strongest = null,
            )
    }

    override fun latestOf(profileId: UUID): LatestFitness? = latest[profileId]

    override fun hasAnyTest(profileIds: Collection<UUID>): Boolean = profileIds.any { it in latest }
}

/** activity 모듈의 가짜: 일별·출처별 기록을 메모리에 쌓고 합계를 낸다. */
class FakeActivity :
    ActivityRecorder,
    ActivityQuery {
    data class Key(
        val profileId: UUID,
        val date: LocalDate,
        val source: ActivitySource,
    )

    val rows = mutableMapOf<Key, DailyActivity>()

    override fun overwriteSteps(
        profileId: UUID,
        activityDate: LocalDate,
        steps: Int,
    ): DailyActivity {
        val row = DailyActivity(profileId, activityDate, ActivitySource.MANUAL, steps, 0)
        rows[Key(profileId, activityDate, ActivitySource.MANUAL)] = row
        return row
    }

    override fun addActiveMinutes(
        profileId: UUID,
        activityDate: LocalDate,
        source: ActivitySource,
        minutes: Int,
    ): DailyActivity {
        val key = Key(profileId, activityDate, source)
        val prev = rows[key]?.activeMinutes ?: 0
        val row = DailyActivity(profileId, activityDate, source, 0, prev + minutes)
        rows[key] = row
        return row
    }

    override fun totals(
        profileId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): ActivityTotals {
        val inRange = rows.values.filter { it.profileId == profileId && !it.activityDate.isBefore(from) && !it.activityDate.isAfter(to) }
        return ActivityTotals(
            steps = inRange.sumOf { it.steps },
            activeMinutes = inRange.sumOf { it.activeMinutes },
            verifiedMinutes = inRange.filter { it.source.serverVerified }.sumOf { it.activeMinutes },
        )
    }

    override fun activeMinutesOn(
        profileId: UUID,
        activityDate: LocalDate,
    ): Int = rows.values.filter { it.profileId == profileId && it.activityDate == activityDate }.sumOf { it.activeMinutes }
}

object Videos {
    fun video(
        id: String,
        ageFrom: Int? = 7,
        ageTo: Int? = 12,
        factors: String = "유연성",
        durationSec: Int? = 600,
        noise: String? = "QUIET",
        space: String? = "SMALL_ROOM",
        equipment: String? = null,
    ) = ExerciseVideo(
        videoId = id,
        title = "영상 $id",
        channelName = "국민체력100",
        channelType = "PUBLIC",
        durationSec = durationSec,
        label = VideoLabel(ageFrom, ageTo, VideoLabel.parseFactors(factors), "LOW", space, noise, null),
        equipment = equipment,
        labeledBy = "SEED",
        collectedAt = Fixed.NOW,
    )

    /** 시드와 같은 다섯 편. */
    fun seed() =
        listOf(
            video("IdpXx2gm90o", 7, 12, "유연성,근지구력", 600),
            video("sample00002", 4, 64, "유연성", 300),
            video("sample00003", 7, 12, "심폐지구력,순발력", 480, noise = "NORMAL"),
            video("sample00004", 19, 64, "근력,근지구력", 720),
            video("sample00005", 4, 6, "평형성,순발력", 420, noise = "NORMAL"),
        )
}
