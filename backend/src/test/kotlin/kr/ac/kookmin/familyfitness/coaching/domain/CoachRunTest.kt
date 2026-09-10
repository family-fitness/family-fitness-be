package kr.ac.kookmin.familyfitness.coaching.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * 테스트 우선으로 제안하는 도메인 계약. 운영 타입은 아직 구현하지 않았다.
 * Approver는 HTTP 요청의 역할 값이 아니라 identity 조회 결과로 만들어야 한다.
 * 실제 DB의 미션 생성·트랜잭션·중복 요청은 후속 모듈 통합 테스트의 책임이다.
 */
class CoachRunTest {
    private val familyId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val parentId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val approvedAt = Instant.parse("2026-09-08T11:00:00Z")
    private val parent = CoachApprover(parentId, familyId, isParent = true)

    @Test
    fun `제안이 준비되어도 승인 대기 상태이고 승인 정보는 없다`() {
        val run = awaitingApproval()

        assertThat(run.status).isEqualTo(CoachRunStatus.AWAITING_APPROVAL)
        assertThat(run.approvedBy).isNull()
        assertThat(run.approvedAt).isNull()
    }

    @Test
    fun `승인 전에는 미션 생성에 사용할 제안을 꺼낼 수 없다`() {
        val run = awaitingApproval()

        assertThrows<CoachApprovalRequiredException> { run.proposalsForMissionCreation() }
        assertAwaitingApproval(run)
    }

    @Test
    fun `같은 가족의 부모가 승인하면 승인자와 시각을 기록한다`() {
        val run = awaitingApproval()

        run.approve(parent, approvedAt)

        assertThat(run.status).isEqualTo(CoachRunStatus.APPROVED)
        assertThat(run.approvedBy).isEqualTo(parentId)
        assertThat(run.approvedAt).isEqualTo(approvedAt)
    }

    @Test
    fun `승인 후에는 원래 제안만 미션 생성 대상으로 반환한다`() {
        val run = awaitingApproval()

        run.approve(parent, approvedAt)

        assertThat(run.proposalsForMissionCreation()).containsExactlyElementsOf(proposals())
    }

    @Test
    fun `자녀는 승인할 수 없고 승인 정보도 남지 않는다`() {
        val run = awaitingApproval()
        val child = CoachApprover(UUID.randomUUID(), familyId, isParent = false)

        assertThrows<ParentRoleRequiredException> { run.approve(child, approvedAt) }

        assertAwaitingApproval(run)
        assertThrows<CoachApprovalRequiredException> { run.proposalsForMissionCreation() }
    }

    @Test
    fun `다른 가족의 부모는 승인할 수 없다`() {
        val run = awaitingApproval()
        val outsider = CoachApprover(UUID.randomUUID(), UUID.randomUUID(), isParent = true)

        assertThrows<CoachFamilyAccessDeniedException> { run.approve(outsider, approvedAt) }

        assertAwaitingApproval(run)
    }

    @Test
    fun `거절하면 사유를 기록하고 미션 생성은 계속 차단한다`() {
        val run = awaitingApproval()

        run.reject(parent, "이번 주는 가족 일정이 있어요")

        assertThat(run.status).isEqualTo(CoachRunStatus.REJECTED)
        assertThat(run.rejectedReason).isEqualTo("이번 주는 가족 일정이 있어요")
        assertThat(run.approvedBy).isNull()
        assertThat(run.approvedAt).isNull()
        assertThrows<CoachApprovalRequiredException> { run.proposalsForMissionCreation() }
    }

    @Test
    fun `중복 승인은 거부하고 최초 승인 기록을 유지한다`() {
        val run = awaitingApproval()
        run.approve(parent, approvedAt)
        val anotherParent = CoachApprover(UUID.randomUUID(), familyId, isParent = true)

        assertThrows<CoachRunAlreadyDecidedException> {
            run.approve(anotherParent, approvedAt.plusSeconds(60))
        }

        assertThat(run.status).isEqualTo(CoachRunStatus.APPROVED)
        assertThat(run.approvedBy).isEqualTo(parentId)
        assertThat(run.approvedAt).isEqualTo(approvedAt)
    }

    @Test
    fun `거절한 실행을 다시 승인할 수 없다`() {
        val run = awaitingApproval()
        run.reject(parent, "다른 운동으로 제안해주세요")

        assertThrows<CoachRunAlreadyDecidedException> { run.approve(parent, approvedAt) }

        assertThat(run.status).isEqualTo(CoachRunStatus.REJECTED)
        assertThat(run.approvedAt).isNull()
    }

    @Test
    fun `승인한 실행을 거절로 바꿀 수 없다`() {
        val run = awaitingApproval()
        run.approve(parent, approvedAt)

        assertThrows<CoachRunAlreadyDecidedException> { run.reject(parent, "취소") }

        assertThat(run.status).isEqualTo(CoachRunStatus.APPROVED)
        assertThat(run.approvedAt).isEqualTo(approvedAt)
    }

    @Test
    fun `자녀는 제안을 거절할 수 없다`() {
        val run = awaitingApproval()
        val child = CoachApprover(UUID.randomUUID(), familyId, isParent = false)

        assertThrows<ParentRoleRequiredException> { run.reject(child, "거절") }

        assertAwaitingApproval(run)
        assertThat(run.rejectedReason).isNull()
    }

    @Test
    fun `다른 가족의 부모는 제안을 거절할 수 없다`() {
        val run = awaitingApproval()
        val outsider = CoachApprover(UUID.randomUUID(), UUID.randomUUID(), isParent = true)

        assertThrows<CoachFamilyAccessDeniedException> { run.reject(outsider, "거절") }

        assertAwaitingApproval(run)
        assertThat(run.rejectedReason).isNull()
    }

    private fun awaitingApproval() =
        CoachRun.awaitingApproval(
            id = UUID.randomUUID(),
            familyId = familyId,
            proposals = proposals(),
        )

    private fun proposals() =
        listOf(
            CoachProposalItem(position = 0, title = "가족 스트레칭", targetMetric = "ACTIVE_MINUTES", targetValue = 5),
            CoachProposalItem(position = 1, title = "함께 걷기", targetMetric = "STEPS", targetValue = 1000),
        )

    private fun assertAwaitingApproval(run: CoachRun) {
        assertThat(run.status).isEqualTo(CoachRunStatus.AWAITING_APPROVAL)
        assertThat(run.approvedBy).isNull()
        assertThat(run.approvedAt).isNull()
    }
}
