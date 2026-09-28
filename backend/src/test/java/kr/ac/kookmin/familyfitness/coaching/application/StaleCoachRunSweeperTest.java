package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Runs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaleCoachRunSweeperTest {
    private final UUID familyId = UUID.randomUUID();
    private final InMemoryCoachRunRepository runs = new InMemoryCoachRunRepository();
    private final CoachRunTimeLimit limit = new CoachRunTimeLimit(1500, 40);
    private final StaleCoachRunSweeper sweeper = new StaleCoachRunSweeper(runs, limit, Fixed.time());

    private CoachRun runningAt(Instant createdAt) {
        return runs.save(Runs.running(familyId, UUID.randomUUID(), Fixed.TODAY, null, createdAt));
    }

    @Test
    @DisplayName("기준 시간 = AI 시작 호출 한도 + 최대 폴링 수 × (폴링 간격 + 폴링 호출 한도) — 기본 설정이면 223초")
    void 기준_시간은_기존_설정에서_계산한다() {
        // (1s + 2s) + 40 × (1.5s + (1s + 3s))
        assertThat(limit.longestRunning()).isEqualTo(Duration.ofSeconds(223));
        // 웹 시험 설정(간격 0 · 3회): 3s + 3 × 4s
        assertThat(new CoachRunTimeLimit(0, 3).longestRunning()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("223초를 넘긴 RUNNING 만 FAILED 로 바꾸고 사유를 남긴다 — 덜 된 RUNNING 과 다른 상태는 그대로다")
    void 기준을_넘긴_RUNNING_만_FAILED_로_바꾼다() {
        CoachRun stale = runningAt(Fixed.NOW.minusSeconds(224));
        CoachRun fresh = runningAt(Fixed.NOW.minusSeconds(222));
        CoachRun awaiting = runs.save(CoachRun.awaitingApproval(UUID.randomUUID(), familyId, List.of()));

        assertThat(sweeper.sweep()).isEqualTo(1);

        assertThat(runs.currentStatus(stale.getId())).isEqualTo(CoachRunStatus.FAILED);
        assertThat(runs.findById(stale.getId()).getFailureReason()).startsWith("stale: 223초");
        assertThat(runs.findById(stale.getId()).lockKey()).isNull();
        assertThat(runs.currentStatus(fresh.getId())).isEqualTo(CoachRunStatus.RUNNING);
        assertThat(runs.currentStatus(awaiting.getId())).isEqualTo(CoachRunStatus.AWAITING_APPROVAL);
        assertThat(sweeper.sweep()).isZero();
    }
}
