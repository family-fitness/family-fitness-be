package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * V169 가 유튜브 클립 가운데 요인이 비어 있던 동작에 공단 영상의 같은 동작 요인을 채웠는지 본다.
 * 요인이 비면 운동 찾기와 제안 순서 줄에 체력 요인이 빠져 보인다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExerciseClipFactorBackfillMigrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("다리뻗어 상체 숙이기 유튜브 클립은 유연성 요인이 붙는다")
    void 다리뻗어_상체_숙이기_클립은_유연성이다() {
        assertThat(factor("Eg3GpTv7z8s-330")).isEqualTo("FLEXIBILITY");
        assertThat(factor("Eg3GpTv7z8s-1770")).isEqualTo("FLEXIBILITY");
        assertThat(factor("HYl0oA1Ptyw-302")).isEqualTo("FLEXIBILITY");
    }

    @Test
    @DisplayName("공단 영상에 요인이 하나로 정해진 동작은 유튜브 클립에도 요인이 비어 있지 않다")
    void 공단_요인이_하나인_동작은_유튜브_클립도_요인이_있다() {
        var empty = jdbc.queryForList("""
                select c.clip_id from video_exercises c
                where c.active = true and c.is_exercise = true and c.fitness_factor is null
                  and (c.source is null or c.source <> 'kspo')
                  and c.exercise_name in (
                    select k.exercise_name from video_exercises k
                    where k.source = 'kspo' and k.fitness_factor is not null
                    group by k.exercise_name
                    having count(distinct k.fitness_factor) = 1)
                """, String.class);
        assertThat(empty).isEmpty();
    }

    @Test
    @DisplayName("공단 영상에서 요인이 둘로 갈리는 동작은 채우지 않는다")
    void 요인이_갈리는_동작은_채우지_않는다() {
        Integer filled = jdbc.queryForObject(
                "select count(*) from video_exercises where exercise_name = ? and (source is null or source <> 'kspo')"
                        + " and fitness_factor is not null",
                Integer.class,
                "엎드려 상체 들어올리기");
        assertThat(filled).isZero();
    }

    private String factor(String clipId) {
        return jdbc.queryForObject(
                "select fitness_factor from video_exercises where clip_id = ?", String.class, clipId);
    }
}
