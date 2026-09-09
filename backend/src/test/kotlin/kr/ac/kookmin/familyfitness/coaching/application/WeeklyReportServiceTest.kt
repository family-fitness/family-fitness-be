package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

class WeeklyReportServiceTest {
    private val family = Family()
    private val identity = FakeIdentity(family).apply { cheerCount = 4 }
    private val activity = FakeActivity()
    private val runs = InMemoryCoachRunRepository()
    private val missions = InMemoryMissionRepository()
    private val policy = MissionCompletionPolicy(activity, InMemoryVideoInteractionRepository(), missions)
    private val missionService = MissionService(missions, InMemoryExerciseVideoRepository(), identity, identity, policy, Fixed.time())
    private val service = WeeklyReportService(runs, missions, identity, identity, activity, identity, policy, Fixed.time())

    @Test
    fun `이번 주 요약은 그 주 최신 실행의 summary·겹치는 미션 집계·구성원 활동·응원 수를 모은다`() {
        runs.save(
            CoachRun.awaitingApproval(
                UUID.randomUUID(),
                family.familyId,
                emptyList(),
                weekStart = Fixed.WEEK_START,
                summary = "옛 요약",
                at = Fixed.NOW.minusSeconds(60),
            ),
        )
        runs.save(
            CoachRun.awaitingApproval(
                UUID.randomUUID(),
                family.familyId,
                emptyList(),
                weekStart = Fixed.WEEK_START,
                summary = "이번 주 요약",
                at = Fixed.NOW,
            ),
        )
        missionService.create(
            family.parentUser,
            family.familyId,
            CreateMissionCommand(
                "타이머",
                Fixed.WEEK_START,
                Fixed.WEEK_START.plusDays(6),
                TargetMetric.TIMER_MINUTES,
                20,
                null,
                listOf(family.child.profileId),
            ),
        )
        missionService.create(
            family.parentUser,
            family.familyId,
            CreateMissionCommand(
                "걸음",
                Fixed.WEEK_START.plusDays(5),
                Fixed.WEEK_START.plusDays(10),
                TargetMetric.STEPS,
                5000,
                null,
                listOf(family.child.profileId, family.parent.profileId),
            ),
        )
        missionService.create(
            family.parentUser,
            family.familyId,
            CreateMissionCommand(
                "지난주",
                Fixed.WEEK_START.minusDays(7),
                Fixed.WEEK_START.minusDays(1),
                TargetMetric.STEPS,
                5000,
                null,
                listOf(family.child.profileId),
            ),
        )
        activity.addActiveMinutes(family.child.profileId, Fixed.TODAY, ActivitySource.TIMER, 25)
        activity.overwriteSteps(family.child.profileId, Fixed.TODAY, 3000)
        activity.addActiveMinutes(family.parent.profileId, Fixed.WEEK_START.minusDays(1), ActivitySource.TIMER, 40)

        val report = service.weekly(family.childUser, family.familyId, null)

        assertThat(report.weekStart).isEqualTo(Fixed.WEEK_START)
        assertThat(report.weekEnd).isEqualTo(Fixed.WEEK_START.plusDays(6))
        assertThat(report.summary).isEqualTo("이번 주 요약")
        assertThat(report.missionStats.total).isEqualTo(2)
        assertThat(report.missionStats.completed).isEqualTo(1)
        assertThat(report.cheerCount).isEqualTo(4)
        val child = report.members.first { it.profileId == family.child.profileId }
        assertThat(child.name).isEqualTo("민준")
        assertThat(child.activeMinutes).isEqualTo(25)
        assertThat(child.verifiedMinutes).isEqualTo(25)
        assertThat(child.completedMissions).isEqualTo(1)
        val parent = report.members.first { it.profileId == family.parent.profileId }
        assertThat(parent.activeMinutes).isEqualTo(0)
        assertThat(parent.completedMissions).isEqualTo(0)
    }

    @Test
    fun `weekStart 는 월요일로 맞추고 다른 가족은 볼 수 없다`() {
        val report = service.weekly(family.parentUser, family.familyId, LocalDate.of(2026, 8, 30))

        assertThat(report.weekStart).isEqualTo(LocalDate.of(2026, 8, 24))
        assertThat(report.summary).isNull()
        assertThat(report.missionStats.total).isEqualTo(0)
        assertThrows<NotSameFamilyException> { service.weekly(family.outsiderUser, family.familyId, null) }
    }
}
