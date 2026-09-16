package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 테스트 우선으로 제안하는 도메인 계약. 운영 타입은 아직 구현하지 않았다.
 * Approver는 HTTP 요청의 역할 값이 아니라 identity 조회 결과로 만들어야 한다.
 * 실제 DB의 미션 생성·트랜잭션·중복 요청은 후속 모듈 통합 테스트의 책임이다.
 */
class CoachRunTest {
    private final UUID familyId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID parentId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final Instant approvedAt = Instant.parse("2026-09-08T11:00:00Z");
    private final CoachApprover parent = new CoachApprover(parentId, familyId, true);

    @Test
    @DisplayName("제안이 준비되어도 승인 대기 상태이고 승인 정보는 없다")
    void 제안이_준비되어도_승인_대기_상태이고_승인_정보는_없다() {
        CoachRun run = awaitingApproval();

        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(run.getApprovedBy()).isNull();
        assertThat(run.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("승인 전에는 미션 생성에 사용할 제안을 꺼낼 수 없다")
    void 승인_전에는_미션_생성에_사용할_제안을_꺼낼_수_없다() {
        CoachRun run = awaitingApproval();

        assertThrows(CoachApprovalRequiredException.class, run::proposalsForMissionCreation);
        assertAwaitingApproval(run);
    }

    @Test
    @DisplayName("같은 가족의 부모가 승인하면 승인자와 시각을 기록한다")
    void 같은_가족의_부모가_승인하면_승인자와_시각을_기록한다() {
        CoachRun run = awaitingApproval();

        run.approve(parent, approvedAt);

        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.APPROVED);
        assertThat(run.getApprovedBy()).isEqualTo(parentId);
        assertThat(run.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("승인 후에는 원래 제안만 미션 생성 대상으로 반환한다")
    void 승인_후에는_원래_제안만_미션_생성_대상으로_반환한다() {
        CoachRun run = awaitingApproval();

        run.approve(parent, approvedAt);

        assertThat(run.proposalsForMissionCreation()).containsExactlyElementsOf(proposals());
    }

    @Test
    @DisplayName("자녀는 승인할 수 없고 승인 정보도 남지 않는다")
    void 자녀는_승인할_수_없고_승인_정보도_남지_않는다() {
        CoachRun run = awaitingApproval();
        CoachApprover child = new CoachApprover(UUID.randomUUID(), familyId, false);

        assertThrows(ParentRoleRequiredException.class, () -> run.approve(child, approvedAt));

        assertAwaitingApproval(run);
        assertThrows(CoachApprovalRequiredException.class, run::proposalsForMissionCreation);
    }

    @Test
    @DisplayName("다른 가족의 부모는 승인할 수 없다")
    void 다른_가족의_부모는_승인할_수_없다() {
        CoachRun run = awaitingApproval();
        CoachApprover outsider = new CoachApprover(UUID.randomUUID(), UUID.randomUUID(), true);

        assertThrows(CoachFamilyAccessDeniedException.class, () -> run.approve(outsider, approvedAt));

        assertAwaitingApproval(run);
    }

    @Test
    @DisplayName("거절하면 사유를 기록하고 미션 생성은 계속 차단한다")
    void 거절하면_사유를_기록하고_미션_생성은_계속_차단한다() {
        CoachRun run = awaitingApproval();

        run.reject(parent, "이번 주는 가족 일정이 있어요");

        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.REJECTED);
        assertThat(run.getRejectedReason()).isEqualTo("이번 주는 가족 일정이 있어요");
        assertThat(run.getApprovedBy()).isNull();
        assertThat(run.getApprovedAt()).isNull();
        assertThrows(CoachApprovalRequiredException.class, run::proposalsForMissionCreation);
    }

    @Test
    @DisplayName("중복 승인은 거부하고 최초 승인 기록을 유지한다")
    void 중복_승인은_거부하고_최초_승인_기록을_유지한다() {
        CoachRun run = awaitingApproval();
        run.approve(parent, approvedAt);
        CoachApprover anotherParent = new CoachApprover(UUID.randomUUID(), familyId, true);

        assertThrows(
                CoachRunAlreadyDecidedException.class, () -> run.approve(anotherParent, approvedAt.plusSeconds(60)));

        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.APPROVED);
        assertThat(run.getApprovedBy()).isEqualTo(parentId);
        assertThat(run.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("거절한 실행을 다시 승인할 수 없다")
    void 거절한_실행을_다시_승인할_수_없다() {
        CoachRun run = awaitingApproval();
        run.reject(parent, "다른 운동으로 제안해주세요");

        assertThrows(CoachRunAlreadyDecidedException.class, () -> run.approve(parent, approvedAt));

        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.REJECTED);
        assertThat(run.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("승인한 실행을 거절로 바꿀 수 없다")
    void 승인한_실행을_거절로_바꿀_수_없다() {
        CoachRun run = awaitingApproval();
        run.approve(parent, approvedAt);

        assertThrows(CoachRunAlreadyDecidedException.class, () -> run.reject(parent, "취소"));

        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.APPROVED);
        assertThat(run.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("자녀는 제안을 거절할 수 없다")
    void 자녀는_제안을_거절할_수_없다() {
        CoachRun run = awaitingApproval();
        CoachApprover child = new CoachApprover(UUID.randomUUID(), familyId, false);

        assertThrows(ParentRoleRequiredException.class, () -> run.reject(child, "거절"));

        assertAwaitingApproval(run);
        assertThat(run.getRejectedReason()).isNull();
    }

    @Test
    @DisplayName("다른 가족의 부모는 제안을 거절할 수 없다")
    void 다른_가족의_부모는_제안을_거절할_수_없다() {
        CoachRun run = awaitingApproval();
        CoachApprover outsider = new CoachApprover(UUID.randomUUID(), UUID.randomUUID(), true);

        assertThrows(CoachFamilyAccessDeniedException.class, () -> run.reject(outsider, "거절"));

        assertAwaitingApproval(run);
        assertThat(run.getRejectedReason()).isNull();
    }

    private CoachRun awaitingApproval() {
        return CoachRun.awaitingApproval(UUID.randomUUID(), familyId, proposals());
    }

    private List<CoachProposalItem> proposals() {
        return List.of(
                new CoachProposalItem(0, "가족 스트레칭", "ACTIVE_MINUTES", 5),
                new CoachProposalItem(1, "함께 걷기", "STEPS", 1000));
    }

    private void assertAwaitingApproval(CoachRun run) {
        assertThat(run.getStatus()).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(run.getApprovedBy()).isNull();
        assertThat(run.getApprovedAt()).isNull();
    }
}
