package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * V168 이 영상과 클립 표에 실린 글에서 가운데 점과 긴 대시를 걷어 냈는지 본다. 이 글은 운동 찾기와 미션 칸에 그대로 나간다.
 * 인용 이름은 AI copy.plain() 과 같이 쉼표로 잇고, 동작 이름은 문맥에 맞춰 「앞뒤로」 「가슴과 어깨」 처럼 고친다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExerciseTextMarksMigrationTest {
    /** 가운데 점으로 보이는 글자와 긴 대시로 보이는 글자. AI copy.py 의 _DOTS 와 _DASHES 를 합친 것이다. */
    private static final Pattern MARKS = Pattern.compile("[·ㆍ・‧∙—–―‒]");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ExerciseVideoRepository videos;

    @Test
    @DisplayName("영상 표의 제목, 채널, 준비물, 인용 이름에 가운데 점과 긴 대시가 없다")
    void 영상_표의_사용자용_칸에_가운데_점과_긴_대시가_없다() {
        assertThat(marked("select video_id, title, channel_name, equipment, citation_label from exercise_videos"))
                .isEmpty();
    }

    @Test
    @DisplayName("클립 표의 화면 이름, 운동 이름, 제목에 가운데 점과 긴 대시가 없다")
    void 클립_표의_사용자용_칸에_가운데_점과_긴_대시가_없다() {
        assertThat(marked("select clip_id, name_on_video, exercise_name, title from video_exercises"))
                .isEmpty();
    }

    @Test
    @DisplayName("인용 이름은 AI 가 내보내는 모양처럼 쉼표로 잇는다")
    void 인용_이름은_쉼표로_잇는다() {
        assertThat(citation("0AUDLJ08S_00181")).isEqualTo("국민체력100 운동처방동영상, 걷기");
        assertThat(citation("0AUDLJ08S_00351")).isEqualTo("국민체력100 운동처방가이드, 팔굽혀펴기");
        assertThat(citation("0AUDLJ08S_00984")).isEqualTo("국민체력100 운동처방가이드, 서서 한팔 들어 오른쪽, 왼쪽 뻗기");
    }

    @Test
    @DisplayName("준비물은 쉼표로 잇고, 동작 이름은 문맥에 맞춰 고친다")
    void 준비물은_쉼표로_잇고_동작_이름은_문맥에_맞춘다() {
        assertThat(jdbc.queryForObject(
                        "select equipment from exercise_videos where video_id = ?", String.class, "0AUDLJ08S_00231"))
                .isEqualTo("덤벨, 물병, 밴드");
        assertThat(title("0AUDLJ08S_00763")).isEqualTo("앞뒤로 선 넘기");
        assertThat(title("0AUDLJ08S_00984")).isEqualTo("서서 한팔 들어 오른쪽과 왼쪽 뻗기");
        assertThat(jdbc.queryForObject(
                        "select name_on_video from video_exercises where clip_id = ?",
                        String.class,
                        "0AUDLJ08S_00763-0"))
                .isEqualTo("앞뒤로 선 넘기");
    }

    private List<String> marked(String sql) {
        return jdbc.queryForList(sql).stream()
                .flatMap((Map<String, Object> row) -> row.values().stream())
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(text -> MARKS.matcher(text).find())
                .distinct()
                .toList();
    }

    private String citation(String videoId) {
        ExerciseVideo video = videos.findById(videoId);
        assertThat(video).isNotNull();
        return video.getCitationLabel();
    }

    private String title(String videoId) {
        ExerciseVideo video = videos.findById(videoId);
        assertThat(video).isNotNull();
        return video.getTitle();
    }
}
