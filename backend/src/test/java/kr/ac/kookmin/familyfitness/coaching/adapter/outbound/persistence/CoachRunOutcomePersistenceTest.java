package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlanSource;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편성이 끝난 모습(V173 plan_source, fallback_reason, finished_at)이 H2 의 실제 행에 쓰이고 다시 읽히는지 확인한다.
 * 파이프라인은 RUNNING 일 때만 쓰는 조건부 update({@link CoachRunJpaRepository#finishIfRunning})로 끝내고, 멈춘 실행은 정리 작업이
 * 따로 FAILED(STALE) 로 바꾼다. 세 길 모두 끝난 시각을 남겨야 편성에 걸린 시간을 셀 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachRunOutcomePersistenceTest {
    private static final Instant STARTED = Instant.parse("2026-10-14T00:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-10-14T00:00:12Z");

    @Autowired
    CoachRunRepository runs;

    @Autowired
    ProfileRows rows;

    private UUID familyId;
    private UUID parentId;
    private UUID kidId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        parentId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2015, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
    }

    private CoachRun running(LocalDate runDate) {
        CoachRun run = CoachRun.start(
                UUID.randomUUID(),
                familyId,
                kidId,
                runDate,
                new CoachRunConditions(20, true, CoachPlace.HOME, null, false),
                parentId,
                STARTED);
        assertThat(runs.insertRunning(run)).isTrue();
        return run;
    }

    @Test
    @DisplayName("대체 편성으로 끝난 실행은 FALLBACK, 그 까닭, 끝난 시각이 행에 남는다")
    void 대체_편성으로_끝나면_까닭과_끝난_시각이_남는다() {
        CoachRun run = running(LocalDate.of(2026, 10, 14));

        run.complete(List.of(), List.of(), null, "대체 편성", null, CoachPlanSource.FALLBACK, "연결 실패", FINISHED);

        assertThat(runs.finishIfRunning(run)).isTrue();
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getPlanSource()).isEqualTo(CoachPlanSource.FALLBACK);
        assertThat(saved.getFallbackReason()).isEqualTo("연결 실패");
        assertThat(saved.getFinishedAt()).isEqualTo(FINISHED);
        assertThat(saved.getCreatedAt()).isEqualTo(STARTED);
    }

    @Test
    @DisplayName("AI 가 짜서 끝난 실행은 AI 로 남고, 거절로 updated_at 이 바뀌어도 끝난 시각은 그대로다")
    void AI_로_끝나면_AI_로_남고_끝난_시각은_거절에도_그대로다() {
        LocalDate runDate = LocalDate.of(2026, 10, 15);
        CoachRun run = running(runDate);
        run.complete(List.of(), List.of(), null, "AI 편성", null, FINISHED);
        assertThat(runs.finishIfRunning(run)).isTrue();

        // 같은 (프로필, 날짜)에 새 편성이 들어와 기다리던 제안이 거절된다(updated_at 이 바뀐다)
        Instant rejectedAt = FINISHED.plusSeconds(600);
        assertThat(runs.rejectAwaitingOf(kidId, runDate, CoachRun.SUPERSEDED_REASON, rejectedAt))
                .isEqualTo(1);

        CoachRun reloaded = runs.findById(run.getId());
        assertThat(reloaded.getUpdatedAt()).isEqualTo(rejectedAt);
        assertThat(reloaded.getPlanSource()).isEqualTo(CoachPlanSource.AI);
        assertThat(reloaded.getFallbackReason()).isNull();
        assertThat(reloaded.getFinishedAt()).isEqualTo(FINISHED);
    }

    @Test
    @DisplayName("FAILED 로 끝난 실행은 누가 짰는지가 비고 끝난 시각만 남는다")
    void FAILED_면_끝난_시각만_남는다() {
        CoachRun run = running(LocalDate.of(2026, 10, 16));

        run.fail(CoachRunFailureCode.AI_FAILED, "failed: 시험", FINISHED);

        assertThat(runs.finishIfRunning(run)).isTrue();
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getPlanSource()).isNull();
        assertThat(saved.getFallbackReason()).isNull();
        assertThat(saved.getFinishedAt()).isEqualTo(FINISHED);
    }

    @Test
    @DisplayName("정리 작업이 멈춘 실행을 FAILED(STALE) 로 바꿀 때도 끝난 시각을 남긴다")
    void 정리_작업도_끝난_시각을_남긴다() {
        CoachRun run = running(LocalDate.of(2026, 10, 17));

        // 다른 시험이 남긴 RUNNING 행을 건드리지 않도록 이 실행의 잠금만 정리한다(failRunningCreatedBefore 와 같은 update 다)
        int cleaned = runs.failStaleLock(
                Objects.requireNonNull(run.lockKey()), STARTED.plusSeconds(1), "stale: 시험", FINISHED);

        assertThat(cleaned).isEqualTo(1);
        CoachRun saved = runs.findById(run.getId());
        assertThat(saved.getFailureCode()).isEqualTo(CoachRunFailureCode.STALE);
        assertThat(saved.getFinishedAt()).isEqualTo(FINISHED);
    }
}
