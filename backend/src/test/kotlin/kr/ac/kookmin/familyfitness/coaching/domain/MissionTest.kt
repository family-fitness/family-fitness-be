package kr.ac.kookmin.familyfitness.coaching.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MissionTest {
    private val familyId = UUID.randomUUID()
    private val parentId = UUID.randomUUID()
    private val childId = UUID.randomUUID()
    private val at = Instant.parse("2026-09-09T01:00:00Z")
    private val monday = LocalDate.of(2026, 9, 7)

    private fun mission(
        metric: TargetMetric,
        target: Int = 45,
        participants: List<UUID> = listOf(childId),
    ) = Mission.manual(
        id = UUID.randomUUID(),
        familyId = familyId,
        title = "함께 운동",
        targetMetric = metric,
        targetValue = target,
        video = null,
        startsOn = monday,
        endsOn = monday.plusDays(6),
        participantProfileIds = participants,
        createdBy = parentId,
        at = at,
    )

    @Test
    fun `타이머 미션은 목표 분에 닿으면 즉시 완료되고 근거는 TIMER 다`() {
        val m = mission(TargetMetric.TIMER_MINUTES, 45)

        m.recordProgress(childId, MissionProgress.of(30, 45, VerifiedBy.TIMER), at)
        val p = m.participantOf(childId)
        assertThat(p.progress).isCloseTo(
            0.666,
            org.assertj.core.data.Offset
                .offset(0.001),
        )
        assertThat(p.completed).isFalse()

        m.recordProgress(childId, MissionProgress.of(60, 45, VerifiedBy.TIMER), at.plusSeconds(1))
        assertThat(p.progress).isEqualTo(1.0)
        assertThat(p.completed).isTrue()
        assertThat(p.verifiedBy).isEqualTo(VerifiedBy.TIMER)
        assertThat(p.verifiedAt).isEqualTo(at.plusSeconds(1))
        assertThat(p.needsGuardianCheck).isFalse()
    }

    @Test
    fun `걸음수 미션은 도달해도 보호자 확인 전에는 완료가 아니다`() {
        val m = mission(TargetMetric.STEPS, 1000)

        m.recordProgress(childId, MissionProgress.of(1500, 1000, null), at)
        val p = m.participantOf(childId)

        assertThat(p.progress).isEqualTo(1.0)
        assertThat(p.completed).isFalse()
        assertThat(p.needsGuardianCheck).isTrue()
        assertThat(m.serverVerifiable).isFalse()

        m.confirm(childId, parentId, at)
        assertThat(p.completed).isTrue()
        assertThat(p.verifiedBy).isEqualTo(VerifiedBy.SELF_REPORT)
        assertThat(p.confirmedBy).isEqualTo(parentId)
        assertThat(p.needsGuardianCheck).isFalse()
    }

    @Test
    fun `목표 도달 전 보호자 확인은 TARGET_NOT_REACHED 다`() {
        val m = mission(TargetMetric.STEPS, 1000)
        m.recordProgress(childId, MissionProgress.of(400, 1000, null), at)

        val e = assertThrows<TargetNotReachedException> { m.confirm(childId, parentId, at) }
        assertThat(e.code).isEqualTo("TARGET_NOT_REACHED")
        assertThat(m.participantOf(childId).completed).isFalse()
    }

    @Test
    fun `완료된 참여자의 진행도는 되돌리지 않는다`() {
        val m = mission(TargetMetric.STEPS, 1000)
        m.recordProgress(childId, MissionProgress.of(1000, 1000, null), at)
        m.confirm(childId, parentId, at)

        val changed = m.recordProgress(childId, MissionProgress.of(200, 1000, null), at.plusSeconds(5))

        assertThat(changed).isFalse()
        assertThat(m.participantOf(childId).progress).isEqualTo(1.0)
    }

    @Test
    fun `참여자가 아니면 NOT_PARTICIPANT, 지표가 다르면 INVALID_METRIC`() {
        val m = mission(TargetMetric.TIMER_MINUTES)

        assertThat(assertThrows<NotParticipantException> { m.participantOf(parentId) }.code).isEqualTo("NOT_PARTICIPANT")
        assertThat(assertThrows<InvalidMetricException> { m.requireMetric(TargetMetric.STEPS) }.code).isEqualTo("INVALID_METRIC")
    }

    @Test
    fun `상태는 전원 완료면 DONE, 기간이 지나면 EXPIRED, 아니면 ACTIVE`() {
        val m = mission(TargetMetric.TIMER_MINUTES, 10, listOf(childId, parentId))

        assertThat(m.statusOn(monday.plusDays(2))).isEqualTo(MissionStatus.ACTIVE)
        assertThat(m.statusOn(monday.plusDays(7))).isEqualTo(MissionStatus.EXPIRED)

        m.recordProgress(childId, MissionProgress.of(10, 10, VerifiedBy.TIMER), at)
        assertThat(m.statusOn(monday.plusDays(2))).isEqualTo(MissionStatus.ACTIVE)
        m.recordProgress(parentId, MissionProgress.of(10, 10, VerifiedBy.TIMER), at)
        assertThat(m.statusOn(monday.plusDays(7))).isEqualTo(MissionStatus.DONE)
    }

    @Test
    fun `진행도는 1을 넘지 않고 목표가 0이면 0이다`() {
        assertThat(MissionProgress.of(120, 45, VerifiedBy.TIMER).progress).isEqualTo(1.0)
        assertThat(MissionProgress.of(5, 0, null).progress).isEqualTo(0.0)
    }

    @Test
    fun `승인된 제안은 기간 없이도 실행의 주로 복사된다`() {
        val run =
            CoachRun.awaitingApproval(
                id = UUID.randomUUID(),
                familyId = familyId,
                weekStart = monday,
                proposals =
                    listOf(
                        CoachProposalItem(
                            position = 0,
                            title = "같이 늘이는 한 주",
                            targetMetric = "TIMER_MINUTES",
                            targetValue = 45,
                            rationale = "부모용 문구",
                            participants =
                                listOf(
                                    ProposalParticipant(childId, kr.ac.kookmin.familyfitness.shared.domain.ProfileRole.CHILD, "주행자"),
                                ),
                            video = ProposalVideo("IdpXx2gm90o", 96),
                        ),
                    ),
            )
        run.approve(CoachApprover(parentId, familyId, true), at)

        val mission = Mission.fromProposal(UUID.randomUUID(), run, run.proposalsForMissionCreation().single(), parentId, at)

        assertThat(mission.origin).isEqualTo(MissionOrigin.COACH)
        assertThat(mission.coachRunId).isEqualTo(run.id)
        assertThat(mission.startsOn).isEqualTo(monday)
        assertThat(mission.endsOn).isEqualTo(monday.plusDays(6))
        assertThat(mission.video).isEqualTo(MissionVideo("IdpXx2gm90o", 96))
        assertThat(mission.participants.single().coachRole).isEqualTo("주행자")
        assertThat(mission.rationale).isEqualTo("부모용 문구")
    }
}
