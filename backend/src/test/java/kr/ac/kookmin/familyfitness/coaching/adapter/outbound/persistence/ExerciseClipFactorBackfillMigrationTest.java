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
    @DisplayName("AI 표에서 공단 요인이 하나인 다섯 동작은 유튜브 클립에도 요인이 비어 있지 않다")
    void AI_표에서_공단_요인이_하나인_동작은_유튜브_클립도_요인이_있다() {
        var empty = jdbc.queryForList("""
                select c.clip_id from video_exercises c
                where c.active = true and c.is_exercise = true and c.fitness_factor is null
                  and (c.source is null or c.source <> 'kspo')
                  and c.exercise_name in ('다리뻗어 상체 숙이기', '다리 벌려 앞으로 상체 숙이기', '나비자세', '걷기',
                                          '의자 앞에서 앉았다 일어서기')
                """, String.class);
        assertThat(empty).isEmpty();
    }

    @Test
    @DisplayName("AI 표에서 공단 요인이 둘로 갈리는 동작은 채우지 않는다")
    void 요인이_갈리는_동작은_채우지_않는다() {
        // AI kspo_videos.csv 에서 엎드려 상체 들어올리기는 유연성과 근력, 무릎 높여 제자리 달리기는 민첩성과 순발력,
        // 윗몸 말아 올리기는 근력과 근지구력이 함께 붙어 있다. BE 는 공단 클립에 요인을 하나만 실어 하나처럼 보인다.
        assertThat(youtubeRowsWith("엎드려 상체 들어올리기", null)).isZero();
        assertThat(youtubeRowsWith("무릎 높여 제자리 달리기", null)).isZero();
        assertThat(youtubeRowsWith("윗몸 말아 올리기", "STRENGTH")).isZero();
    }

    /** 요인이 채워진 유튜브 줄 수. {@code factor} 가 null 이면 어느 요인이든 센다. */
    private int youtubeRowsWith(String exerciseName, String factor) {
        String youtube = "select count(*) from video_exercises where exercise_name = ?"
                + " and (source is null or source <> 'kspo') and fitness_factor is not null";
        Integer rows = factor == null
                ? jdbc.queryForObject(youtube, Integer.class, exerciseName)
                : jdbc.queryForObject(youtube + " and fitness_factor = ?", Integer.class, exerciseName, factor);
        return rows == null ? 0 : rows;
    }

    private String factor(String clipId) {
        return jdbc.queryForObject(
                "select fitness_factor from video_exercises where clip_id = ?", String.class, clipId);
    }
}
