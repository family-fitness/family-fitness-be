package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.domain.AlreadyRunThisWeekException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunAlreadyDecidedException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunInProgressException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin
import kr.ac.kookmin.familyfitness.coaching.domain.NoMeasuredMemberException
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository
import kr.ac.kookmin.familyfitness.coaching.support.Videos
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.UUID

class CoachRunServiceTest {
    private val family = Family()
    private val identity = FakeIdentity(family)
    private val fitness = FakeFitness()
    private val runs = InMemoryCoachRunRepository()
    private val missions = InMemoryMissionRepository()
    private val videos = InMemoryExerciseVideoRepository(Videos.seed())
    private val events = mutableListOf<Any>()
    private val service =
        CoachRunService(
            runs,
            missions,
            videos,
            identity,
            identity,
            fitness,
            ApplicationEventPublisher { events += it },
            Fixed.time(),
        )
    private val command = StartCoachRunCommand(weekStart = null, daysPerWeek = 3, minutesPerSession = 15)

    @BeforeEach
    fun measured() {
        fitness.measured(family.child.profileId, "012" to 8.0)
    }

    @Test
    fun `시작하면 이번 주 월요일의 RUNNING 실행이 저장되고 이벤트가 커밋 후 처리를 위해 발행된다`() {
        val accepted = service.start(family.childUser, family.familyId, command)

        assertThat(accepted.status).isEqualTo(CoachRunStatus.RUNNING)
        assertThat(accepted.pollAfterMs).isEqualTo(1500)
        val run = runs.findById(accepted.coachRunId)!!
        assertThat(run.weekStart).isEqualTo(Fixed.WEEK_START)
        assertThat(run.triggerType).isEqualTo(TriggerType.MANUAL)
        assertThat(run.requestedBy).isEqualTo(family.child.profileId)
        assertThat(run.daysPerWeek).isEqualTo(3)
        assertThat(events).containsExactly(CoachRunRequested(run.id))
    }

    @Test
    fun `weekStart 는 아무 요일이나 받아 그 주 월요일로 맞춘다`() {
        val accepted = service.start(family.parentUser, family.familyId, command.copy(weekStart = LocalDate.of(2026, 9, 17)))

        assertThat(runs.findById(accepted.coachRunId)!!.weekStart).isEqualTo(LocalDate.of(2026, 9, 14))
    }

    @Test
    fun `같은 가족에 RUNNING 실행이 있으면 RUN_IN_PROGRESS`() {
        service.start(family.parentUser, family.familyId, command)

        val e =
            assertThrows<CoachRunInProgressException> {
                service.start(family.parentUser, family.familyId, command.copy(weekStart = LocalDate.of(2026, 9, 21)))
            }
        assertThat(e.code).isEqualTo("RUN_IN_PROGRESS")
    }

    @Test
    fun `같은 주에 승인 대기·승인된 실행이 있으면 ALREADY_RUN_THIS_WEEK, 거절·실패면 다시 시작할 수 있다`() {
        val awaiting = CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals(), weekStart = Fixed.WEEK_START)
        runs.save(awaiting)
        assertThat(assertThrows<AlreadyRunThisWeekException> { service.start(family.parentUser, family.familyId, command) }.code)
            .isEqualTo("ALREADY_RUN_THIS_WEEK")

        awaiting.reject(parentApprover(), "다음에", Fixed.NOW)
        runs.save(awaiting)
        assertThat(service.start(family.parentUser, family.familyId, command).status).isEqualTo(CoachRunStatus.RUNNING)
    }

    @Test
    fun `측정 기록이 있는 구성원이 없으면 NO_MEASURED_MEMBER`() {
        fitness.latest.clear()

        assertThat(assertThrows<NoMeasuredMemberException> { service.start(family.parentUser, family.familyId, command) }.code)
            .isEqualTo("NO_MEASURED_MEMBER")
        assertThat(runs.runs).isEmpty()
    }

    @Test
    fun `다른 가족 계정은 시작·조회할 수 없다`() {
        assertThrows<NotSameFamilyException> { service.start(family.outsiderUser, family.familyId, command) }
        val run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals()))
        assertThrows<NotSameFamilyException> { service.get(family.outsiderUser, run.id) }
        assertThrows<CoachRunNotFoundException> { service.get(family.parentUser, UUID.randomUUID()) }
    }

    @Test
    fun `조회는 부모에게만 canApprove 를 주고 제안에 영상 제목·배지를 붙인다`() {
        val run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals(), weekStart = Fixed.WEEK_START))

        val parentView = service.get(family.parentUser, run.id)
        val childView = service.get(family.childUser, run.id)

        assertThat(parentView.canApprove).isTrue()
        assertThat(childView.canApprove).isFalse()
        assertThat(parentView.status).isEqualTo(CoachRunStatus.AWAITING_APPROVAL)
        assertThat(parentView.missionCount).isEqualTo(0)
        val proposal = parentView.proposals!!.single()
        assertThat(proposal.video!!.title).isEqualTo("영상 IdpXx2gm90o")
        assertThat(proposal.video!!.url).isEqualTo("https://www.youtube.com/watch?v=IdpXx2gm90o")
        assertThat(proposal.video!!.badges).containsExactly("조용함", "좁은 공간 OK", "준비물 없음")
        assertThat(proposal.startDate).isEqualTo(Fixed.WEEK_START)
        assertThat(proposal.endDate).isEqualTo(Fixed.WEEK_START.plusDays(6))
        assertThat(proposal.participants.single().coachRole).isEqualTo("주행자")
    }

    @Test
    fun `부모가 승인하면 한 번만 미션이 만들어지고 승인자는 부모 프로필이다`() {
        val run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals(), weekStart = Fixed.WEEK_START))

        val view = service.approve(family.parentUser, run.id)

        assertThat(view.status).isEqualTo(CoachRunStatus.APPROVED)
        assertThat(view.approvedBy).isEqualTo(family.parent.profileId)
        assertThat(view.approvedAt).isEqualTo(Fixed.NOW)
        assertThat(view.createdMissions).hasSize(1)
        assertThat(view.createdMissions.single().origin).isEqualTo(MissionOrigin.COACH)
        val mission = missions.findById(view.createdMissions.single().missionId)!!
        assertThat(mission.coachRunId).isEqualTo(run.id)
        assertThat(mission.participants.map { it.profileId }).containsExactly(family.child.profileId)
        assertThat(service.get(family.parentUser, run.id).missionCount).isEqualTo(1)
        assertThat(service.get(family.parentUser, run.id).canApprove).isFalse()

        val again = assertThrows<CoachRunAlreadyDecidedException> { service.approve(family.parentUser, run.id) }
        assertThat(again.code).isEqualTo("ALREADY_APPROVED")
        assertThat(missions.missions).hasSize(1)
    }

    @Test
    fun `자녀·다른 가족·거절된 실행은 승인할 수 없다`() {
        val run = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), family.familyId, proposals()))

        assertThrows<NotAParentException> { service.approve(family.childUser, run.id) }
        assertThrows<NotSameFamilyException> { service.approve(family.outsiderUser, run.id) }
        assertThat(missions.missions).isEmpty()

        val rejected = service.reject(family.parentUser, run.id, "이번 주는 쉬어요")
        assertThat(rejected.status).isEqualTo(CoachRunStatus.REJECTED)
        assertThat(rejected.rejectedReason).isEqualTo("이번 주는 쉬어요")
        assertThat(rejected.missionCount).isEqualTo(0)

        assertThat(
            assertThrows<CoachRunAlreadyDecidedException> { service.approve(family.parentUser, run.id) }.code,
        ).isEqualTo("INVALID_STATE")
        assertThat(
            assertThrows<CoachRunAlreadyDecidedException> {
                service.reject(family.parentUser, run.id, null)
            }.code,
        ).isEqualTo("INVALID_STATE")
    }

    @Test
    fun `참여자 없는 제안 항목은 미션으로 만들지 않는다`() {
        val run =
            runs.save(
                CoachRun.awaitingApproval(
                    UUID.randomUUID(),
                    family.familyId,
                    listOf(CoachProposalItem(position = 0, title = "빈 항목", targetMetric = "TIMER_MINUTES", targetValue = 30)),
                ),
            )

        assertThat(service.approve(family.parentUser, run.id).createdMissions).isEmpty()
    }

    private fun proposals() =
        listOf(
            CoachProposalItem(
                position = 0,
                title = "같이 늘이는 한 주",
                targetMetric = "TIMER_MINUTES",
                targetValue = 45,
                rationale = "부모 문구",
                participants = listOf(ProposalParticipant(family.child.profileId, ProfileRole.CHILD, "주행자")),
                video = ProposalVideo("IdpXx2gm90o", 96),
            ),
        )

    private fun parentApprover() =
        kr.ac.kookmin.familyfitness.coaching.domain
            .CoachApprover(family.parent.profileId, family.familyId, true)
}
