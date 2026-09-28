package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** H2 에 Flyway 로 V132(AI 클립 적재)를 적용한 결과를 포트로 읽는다. 수는 AI 커밋 2af9002 판 기준이다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExerciseClipPersistenceAdapterTest {
    @Autowired
    ExerciseClipRepository clips;

    @Autowired
    ExerciseVideoRepository videos;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("V132 는 클립 695개를 모두 싣고, 그중 운동 클립은 47편 651개다")
    void V132_는_클립_695개를_모두_싣고_그중_운동_클립은_47편_651개다() {
        List<ExerciseClip> all = clips.findAllActive();
        List<ExerciseClip> exercises =
                all.stream().filter(ExerciseClip::isExercise).toList();

        assertThat(all).hasSize(695);
        assertThat(all).allSatisfy(it -> {
            assertThat(it.clipId()).isEqualTo(ExerciseClip.idOf(it.videoId(), it.startSec()));
            assertThat(it.endSec()).isGreaterThan(it.startSec());
        });
        assertThat(all.stream().map(ExerciseClip::videoId).distinct()).hasSize(48);
        assertThat(exercises).hasSize(651);
        assertThat(exercises.stream().map(ExerciseClip::videoId).distinct()).hasSize(47);
        assertThat(exercises.stream().collect(groupingBy(ExerciseClip::phase, counting())))
                .isEqualTo(Map.of(SessionPhase.MAIN, 353L, SessionPhase.WARMUP, 158L, SessionPhase.COOLDOWN, 140L));
        assertThat(exercises.stream().collect(groupingBy(ExerciseClip::ageGroup, counting())))
                .isEqualTo(Map.of(
                        AgeGroup.TODDLER, 180L, AgeGroup.YOUTH, 133L, AgeGroup.ADOLESCENT, 157L, AgeGroup.ADULT, 181L));
    }

    @Test
    @DisplayName("단계는 영상 화면 표시를 먼저 쓰고, 표시가 없을 때만 라벨 단계를 쓴다")
    void 단계는_영상_화면_표시를_먼저_쓰고_표시가_없을_때만_라벨_단계를_쓴다() {
        // 화면 표시 없음 · 라벨 준비운동 → 준비. 처방 어휘가 없어 제목은 화면 이름이다.
        assertThat(clips.findById("IdpXx2gm90o-56"))
                .isEqualTo(new ExerciseClip(
                        "IdpXx2gm90o-56",
                        "IdpXx2gm90o",
                        1,
                        "스트레칭",
                        null,
                        "스트레칭",
                        FitnessFactor.FLEXIBILITY,
                        SessionPhase.WARMUP,
                        56,
                        196,
                        true,
                        true,
                        false,
                        true,
                        AgeGroup.YOUTH,
                        "llm",
                        true));
        // 화면 표시 본운동 · 라벨 준비운동 → 본. 처방 어휘가 있어 제목은 그쪽이다.
        assertThat(clips.findById("IdpXx2gm90o-198"))
                .isEqualTo(new ExerciseClip(
                        "IdpXx2gm90o-198",
                        "IdpXx2gm90o",
                        2,
                        "팔 벌려 뛰기",
                        "팔벌려뛰기",
                        "팔벌려뛰기",
                        FitnessFactor.CARDIO,
                        SessionPhase.MAIN,
                        198,
                        262,
                        true,
                        false,
                        false,
                        true,
                        AgeGroup.YOUTH,
                        "human",
                        true));
    }

    @Test
    @DisplayName("운동이 아닌 구간도 행으로 남기고 isExercise 로만 가른다")
    void 운동이_아닌_구간도_행으로_남기고_isExercise_로만_가른다() {
        ExerciseClip rest = clips.findById("AW9qNySmp6I-192");

        assertThat(rest).isNotNull();
        assertThat(rest.title()).isEqualTo("휴식");
        assertThat(rest.isExercise()).isFalse();
        assertThat(rest.factor()).isNull();
        assertThat(clips.findById("nope-0")).isNull();
    }

    @Test
    @DisplayName("새 판에서 빠져 꺼진 클립은 목록에서 빠지고, id 로는 여전히 찾힌다")
    void 새_판에서_빠져_꺼진_클립은_목록에서_빠지고_id_로는_여전히_찾힌다() {
        // 다음 릴리스 SQL 이 이번 판에 없는 클립에 하는 일과 같다(scripts/ai_clips_to_sql.py deactivate_statement).
        jdbc.update("update video_exercises set active = false where clip_id = ?", "IdpXx2gm90o-56");

        assertThat(clips.findAllActive())
                .hasSize(694)
                .extracting(ExerciseClip::clipId)
                .doesNotContain("IdpXx2gm90o-56");
        ExerciseClip retired = clips.findById("IdpXx2gm90o-56");
        assertThat(retired).isNotNull();
        assertThat(retired.active()).isFalse();
    }

    @Test
    @DisplayName("영상 48편은 코퍼스 제목 · 연령 범위 · 영상 요인만 싣고, 자료에 없는 길이는 비워 둔다")
    void 영상_48편은_코퍼스_제목_연령_범위_영상_요인만_싣고_자료에_없는_길이는_비워_둔다() {
        List<String> videoIds = clips.findAllActive().stream()
                .map(ExerciseClip::videoId)
                .distinct()
                .toList();
        assertThat(videos.findAllByIds(videoIds)).hasSize(48);

        ExerciseVideo youth = videos.findById("IdpXx2gm90o");
        assertThat(youth).isNotNull();
        assertThat(youth.getTitle()).isEqualTo("초등학생의 기초체력향상과 운동능력발달을 위한 운동! 같이해봐요! #국민체력100 #유소년 #어린이운동");
        assertThat(youth.getLabeledBy()).isEqualTo("AI");
        assertThat(youth.getDurationSec()).isNull();
        assertThat(youth.getLabel().ageFrom()).isEqualTo(7);
        assertThat(youth.getLabel().ageTo()).isEqualTo(12);
        assertThat(youth.getLabel().factors()).isEmpty();

        ExerciseVideo strength = videos.findById("Eg3GpTv7z8s");
        assertThat(strength).isNotNull();
        assertThat(strength.getLabel().factors()).containsExactly("근력");
    }
}
