package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class WeeklyReportView(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val summary: String?,
    val missionStats: MissionStatsView,
    val members: List<MemberReportView>,
    val cheerCount: Int,
)

data class MissionStatsView(
    val total: Int,
    val completed: Int,
)

data class MemberReportView(
    val profileId: UUID,
    val name: String,
    val activeMinutes: Int,
    val verifiedMinutes: Int,
    val completedMissions: Int,
)

/** 주간 요약(월~일). 그 주에 겹치는 미션과 구성원 활동, 응원 수를 모은다. */
@Service
class WeeklyReportService(
    private val runs: CoachRunRepository,
    private val missions: MissionRepository,
    private val familyAccess: FamilyAccess,
    private val profileQuery: ProfileQuery,
    private val activityQuery: ActivityQuery,
    private val cheerQuery: CheerQuery,
    private val policy: MissionCompletionPolicy,
    private val time: AppTime,
) {
    @Transactional
    fun weekly(
        userId: UUID,
        familyId: UUID,
        weekStartParam: LocalDate?,
    ): WeeklyReportView {
        familyAccess.requireMember(userId, familyId)
        val weekStart = weekStartParam?.let(AppTime::weekStartOf) ?: time.thisWeekStart()
        val weekEnd = weekStart.plusDays(6)
        val now = time.now()

        val weekMissions = missions.findOverlapping(familyId, weekStart, weekEnd).map { policy.refreshAll(it, now) }
        val members =
            profileQuery.summariesOfFamily(familyId).map { member ->
                val totals = activityQuery.totals(member.profileId, weekStart, weekEnd)
                MemberReportView(
                    profileId = member.profileId,
                    name = member.name,
                    activeMinutes = totals.activeMinutes,
                    verifiedMinutes = totals.verifiedMinutes,
                    completedMissions =
                        weekMissions.count { m -> m.participants.any { it.profileId == member.profileId && it.completed } },
                )
            }
        return WeeklyReportView(
            weekStart = weekStart,
            weekEnd = weekEnd,
            summary = runs.findLatestOfWeek(familyId, weekStart)?.summary,
            missionStats = MissionStatsView(total = weekMissions.size, completed = weekMissions.count { it.allCompleted }),
            members = members,
            cheerCount = cheerQuery.countCheers(familyId, time.startOfDay(weekStart), time.startOfDay(weekEnd.plusDays(1))),
        )
    }
}
