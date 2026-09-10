package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidMetricException
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException
import kr.ac.kookmin.familyfitness.coaching.domain.NotParticipantException
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.domain.TargetNotReachedException
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository
import kr.ac.kookmin.familyfitness.coaching.support.Videos
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class MissionServiceTest {
    private val family = Family()
    private val other = Family()
    private val identity = FakeIdentity(family, other)
    private val activity = FakeActivity()
    private val missions = InMemoryMissionRepository()
    private val interactions = InMemoryVideoInteractionRepository()
    private val videos = InMemoryExerciseVideoRepository(Videos.seed())
    private val policy = MissionCompletionPolicy(activity, interactions, missions)
    private val service = MissionService(missions, videos, identity, identity, policy, Fixed.time())
    private val activityService = MissionActivityService(missions, identity, activity, activity, policy, Fixed.time())

    private fun create(
        metric: TargetMetric = TargetMetric.TIMER_MINUTES,
        target: Int = 45,
        participants: List<UUID> = listOf(family.child.profileId),
        videoId: String? = null,
    ) = service.create(
        family.parentUser,
        family.familyId,
        CreateMissionCommand("함께 운동", Fixed.WEEK_START, Fixed.WEEK_START.plusDays(6), metric, target, videoId, participants),
    )

    @Test
    fun `부모는 같은 가족 참여자로 미션을 직접 만든다`() {
        val created = create(videoId = "IdpXx2gm90o")

        assertThat(created.origin).isEqualTo(MissionOrigin.MANUAL)
        assertThat(created.coachRunId).isNull()
        assertThat(created.serverVerifiable).isTrue()
        val mission = missions.findById(created.missionId)!!
        assertThat(mission.createdBy).isEqualTo(family.parent.profileId)
        assertThat(mission.video!!.videoId).isEqualTo("IdpXx2gm90o")
        assertThat(mission.participants.single().profileId).isEqualTo(family.child.profileId)
    }

    @Test
    fun `자녀·다른 가족 참여자·없는 영상은 거부된다`() {
        assertThrows<NotAParentException> {
            service.create(
                family.childUser,
                family.familyId,
                CreateMissionCommand("t", Fixed.TODAY, Fixed.TODAY, TargetMetric.STEPS, 1, null, listOf(family.child.profileId)),
            )
        }
        assertThat(
            assertThrows<NotFamilyMemberException> {
                create(participants = listOf(other.child.profileId))
            }.code,
        ).isEqualTo("NOT_FAMILY_MEMBER")
        assertThat(assertThrows<VideoNotFoundException> { create(videoId = "nope") }.code).isEqualTo("VIDEO_NOT_FOUND")
        assertThat(create(TargetMetric.STEPS, 3000).serverVerifiable).isFalse()
    }

    @Test
    fun `타이머 기록은 경과 시간으로 자르고 진행도를 갱신하며 목표에 닿으면 완료된다`() {
        val missionId = create(TargetMetric.TIMER_MINUTES, 30).missionId
        val startedAt = Instant.parse("2026-09-08T23:30:00Z") // KST 9/9 08:30

        val first =
            activityService.recordTimer(
                family.childUser,
                missionId,
                RecordTimerCommand(family.child.profileId, startedAt, startedAt.plusSeconds(20 * 60), activeMinutes = 60),
            )
        assertThat(first.activityDate).isEqualTo(Fixed.TODAY)
        assertThat(first.source).isEqualTo(ActivitySource.TIMER)
        assertThat(first.serverVerified).isTrue()
        assertThat(first.totalActiveMinutes).isEqualTo(20)
        assertThat(first.missionProgress).isCloseTo(
            0.666,
            org.assertj.core.data.Offset
                .offset(0.001),
        )
        assertThat(first.missionCompleted).isFalse()

        val second =
            activityService.recordTimer(
                family.parentUser,
                missionId,
                RecordTimerCommand(
                    family.child.profileId,
                    startedAt.plusSeconds(3600),
                    startedAt.plusSeconds(3600 + 15 * 60),
                    activeMinutes = 10,
                ),
            )
        assertThat(second.totalActiveMinutes).isEqualTo(30)
        assertThat(second.missionProgress).isEqualTo(1.0)
        assertThat(second.missionCompleted).isTrue()
        assertThat(missions.findById(missionId)!!.participantOf(family.child.profileId).verifiedBy).isEqualTo(VerifiedBy.TIMER)
    }

    @Test
    fun `걸음수는 그날 총량으로 덮어쓰고 도달해도 보호자 확인이 남는다`() {
        val missionId = create(TargetMetric.STEPS, 5000).missionId

        activityService.recordSteps(family.childUser, missionId, RecordStepsCommand(family.child.profileId, Fixed.TODAY, 3000))
        val view = activityService.recordSteps(family.childUser, missionId, RecordStepsCommand(family.child.profileId, Fixed.TODAY, 6000))

        assertThat(view.source).isEqualTo(ActivitySource.MANUAL)
        assertThat(view.serverVerified).isFalse()
        assertThat(view.verifiedBy).isEqualTo(VerifiedBy.SELF_REPORT)
        assertThat(view.missionProgress).isEqualTo(1.0)
        assertThat(view.missionCompleted).isFalse()
        assertThat(view.needsGuardianCheck).isTrue()
        assertThat(activity.totals(family.child.profileId, Fixed.WEEK_START, Fixed.WEEK_START.plusDays(6)).steps).isEqualTo(6000)

        val confirmed = service.confirm(family.parentUser, missionId, family.child.profileId)
        assertThat(confirmed.completed).isTrue()
        assertThat(confirmed.verifiedBy).isEqualTo(VerifiedBy.SELF_REPORT)
        assertThat(confirmed.confirmedBy).isEqualTo(family.parent.profileId)
        assertThat(confirmed.verifiedAt).isEqualTo(Fixed.NOW)
    }

    @Test
    fun `활동 기록의 규칙 위반은 코드로 구분된다`() {
        val steps = create(TargetMetric.STEPS, 5000).missionId
        val timer = create(TargetMetric.TIMER_MINUTES, 30).missionId
        val now = Fixed.NOW

        assertThat(
            assertThrows<NotParticipantException> {
                activityService.recordSteps(family.parentUser, steps, RecordStepsCommand(family.parent.profileId, Fixed.TODAY, 100))
            }.code,
        ).isEqualTo("NOT_PARTICIPANT")
        assertThat(
            assertThrows<InvalidMetricException> {
                activityService.recordSteps(family.parentUser, timer, RecordStepsCommand(family.child.profileId, Fixed.TODAY, 100))
            }.code,
        ).isEqualTo("INVALID_METRIC")
        assertThat(
            assertThrows<InvalidMetricException> {
                activityService.recordTimer(
                    family.parentUser,
                    steps,
                    RecordTimerCommand(family.child.profileId, now, now.plusSeconds(600), 5),
                )
            }.code,
        ).isEqualTo("INVALID_METRIC")
        assertThrows<NotSameFamilyException> {
            activityService.recordSteps(other.parentUser, steps, RecordStepsCommand(family.child.profileId, Fixed.TODAY, 100))
        }
        assertThrows<IllegalArgumentException> {
            activityService.recordSteps(family.parentUser, steps, RecordStepsCommand(family.child.profileId, Fixed.TODAY.plusDays(1), 100))
        }
        assertThrows<IllegalArgumentException> {
            activityService.recordTimer(family.parentUser, timer, RecordTimerCommand(family.child.profileId, now, now, 5))
        }
        assertThat(assertThrows<TargetNotReachedException> { service.confirm(family.parentUser, steps, family.child.profileId) }.code)
            .isEqualTo("TARGET_NOT_REACHED")
        assertThrows<NotAParentException> { service.confirm(family.childUser, steps, family.child.profileId) }
    }

    @Test
    fun `목록은 scope·status 로 거르고 읽을 때 진행도를 다시 계산한다`() {
        val mine = create(TargetMetric.TIMER_MINUTES, 30, listOf(family.child.profileId)).missionId
        val familyWide = create(TargetMetric.STEPS, 100, listOf(family.child.profileId, family.parent.profileId)).missionId
        val expired =
            service
                .create(
                    family.parentUser,
                    family.familyId,
                    CreateMissionCommand(
                        "지난 미션",
                        Fixed.WEEK_START.minusDays(7),
                        Fixed.WEEK_START.minusDays(1),
                        TargetMetric.STEPS,
                        100,
                        null,
                        listOf(family.child.profileId),
                    ),
                ).missionId
        activity.addActiveMinutes(family.child.profileId, Fixed.TODAY, ActivitySource.VIDEO, 30)

        val all = service.list(family.childUser, family.familyId, MissionScope.ALL, null)
        assertThat(all.missions.map { it.missionId }).containsExactlyInAnyOrder(mine, familyWide, expired)
        val mineView = all.missions.first { it.missionId == mine }
        assertThat(mineView.participants.single().progress).isEqualTo(1.0)
        assertThat(mineView.participants.single().completed).isTrue()
        assertThat(mineView.participants.single().name).isEqualTo("민준")
        assertThat(mineView.serverVerifiable).isTrue()

        assertThat(service.list(family.childUser, family.familyId, MissionScope.MINE, null).missions.map { it.missionId })
            .containsExactlyInAnyOrder(mine, familyWide, expired)
        assertThat(service.list(family.parentUser, family.familyId, MissionScope.MINE, null).missions.map { it.missionId })
            .containsExactly(familyWide)
        assertThat(service.list(family.parentUser, family.familyId, MissionScope.FAMILY, null).missions.map { it.missionId })
            .containsExactly(familyWide)
        assertThat(service.list(family.parentUser, family.familyId, MissionScope.ALL, MissionStatus.DONE).missions.map { it.missionId })
            .containsExactly(mine)
        assertThat(service.list(family.parentUser, family.familyId, MissionScope.ALL, MissionStatus.EXPIRED).missions.map { it.missionId })
            .containsExactly(expired)
        assertThat(service.list(family.parentUser, family.familyId, MissionScope.ALL, MissionStatus.ACTIVE).missions.map { it.missionId })
            .containsExactly(familyWide)
        assertThrows<NotSameFamilyException> { service.list(other.parentUser, family.familyId, MissionScope.ALL, null) }
    }
}
