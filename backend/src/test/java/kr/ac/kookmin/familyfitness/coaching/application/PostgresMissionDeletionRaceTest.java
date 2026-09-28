package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import kr.ac.kookmin.familyfitness.TestcontainersConfiguration;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionAlreadyStartedException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영과 같은 PostgreSQL(Testcontainers, READ COMMITTED)에서 돈다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는 돈다).
 *
 * <p>여기에만 있는 두 시험은 PostgreSQL 의 행 잠금 규칙에 기댄다: 칸 끝 행을 넣을 때 외래 키 검사가 미션 행에 FOR KEY SHARE 를 걸고,
 * 이것은 지우기의 FOR UPDATE 와 부딪친다(FOR NO KEY UPDATE 와는 부딪치지 않는다). H2 는 외래 키 검사 때 부모 행을 잠그지 않아
 * 같은 차례를 재현할 수 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostgresMissionDeletionRaceTest extends MissionDeletionRaceTestBase {
    @Test
    @DisplayName("지우기가 미션 행을 잡고 있는 동안 온 칸 끝은 칸 끝 행을 넣다가 기다렸다가 404 — 지우기는 끝까지 간다")
    void 지우기가_잡고_있는_동안_온_칸_끝은_기다렸다가_404() throws InterruptedException {
        Pause pause = new Pause();
        MissionDeletionService deleting = deletion(new PausedFeedbacks(feedbacks, pause));
        SessionCompletionService completing = completion(completions);

        Race<Void, SessionCompletedView> race = race(
                pause,
                run(() -> deleting.delete(momUser, missionId)),
                call(() -> completing.complete(momUser, missionId, 1, oneMinute())));

        resultOf(race.first());
        assertThat(failureOf(race.second())).isInstanceOf(MissionNotFoundException.class);
        assertThat(rowsOf("missions", "id")).isZero();
        assertThat(rowsOf("mission_session_completions", "mission_id")).isZero();
        assertThat(activeSecondsOfKid()).isZero();
    }

    @Test
    @DisplayName("칸 끝이 먼저 칸 끝 행을 넣었으면, 지우기는 그 트랜잭션이 끝나길 기다렸다가 409 MISSION_ALREADY_STARTED")
    void 칸_끝이_먼저_넣었으면_지우기는_기다렸다가_409() throws InterruptedException {
        Pause pause = new Pause();
        SessionCompletionService completing = completion(new HookedCompletions(completions, () -> {}, pause::hold));
        MissionDeletionService deleting = deletion(feedbacks);

        Race<SessionCompletedView, Void> race = race(
                pause,
                call(() -> completing.complete(momUser, missionId, 1, oneMinute())),
                run(() -> deleting.delete(momUser, missionId)));

        SessionCompletedView done = resultOf(race.first());
        assertThat(done).isNotNull();
        assertThat(done.missionCompleted()).isTrue();
        assertThat(failureOf(race.second())).isInstanceOf(MissionAlreadyStartedException.class);
        assertThat(rowsOf("missions", "id")).isOne();
        assertThat(rowsOf("mission_session_completions", "mission_id")).isOne();
    }
}
