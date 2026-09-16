package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import kr.ac.kookmin.familyfitness.shared.domain.Band;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradeTest {
    @Test
    @DisplayName("등급은 잠정 임계값 90·75·50 으로 나뉜다")
    void 등급은_잠정_임계값_90_75_50_으로_나뉜다() {
        assertThat(Grade.ofPercentile(99)).isEqualTo(Grade.FIRST);
        assertThat(Grade.ofPercentile(90)).isEqualTo(Grade.FIRST);
        assertThat(Grade.ofPercentile(89)).isEqualTo(Grade.SECOND);
        assertThat(Grade.ofPercentile(75)).isEqualTo(Grade.SECOND);
        assertThat(Grade.ofPercentile(74)).isEqualTo(Grade.THIRD);
        assertThat(Grade.ofPercentile(50)).isEqualTo(Grade.THIRD);
        assertThat(Grade.ofPercentile(49)).isEqualTo(Grade.PARTICIPATION);
        assertThat(Grade.ofPercentile(1)).isEqualTo(Grade.PARTICIPATION);
    }

    @Test
    @DisplayName("백분위 하나에서 등급·구간·상위 문구가 함께 파생된다")
    void 백분위_하나에서_등급_구간_상위_문구가_함께_파생된다() {
        ItemScore score = ItemScore.ofPercentile(24);
        assertThat(score.grade()).isEqualTo(Grade.PARTICIPATION);
        assertThat(score.band()).isEqualTo(Band.GROWTH);
        assertThat(score.topPercentText()).isEqualTo("상위 76%");

        ItemScore strong = ItemScore.ofPercentile(75);
        assertThat(strong.grade()).isEqualTo(Grade.SECOND);
        assertThat(strong.band()).isEqualTo(Band.STRENGTH);
        assertThat(strong.topPercentText()).isEqualTo("상위 25%");
    }

    @Test
    @DisplayName("규준이 없으면 전부 null")
    void 규준이_없으면_전부_null() {
        assertThat(ItemScore.ofPercentile(null)).isEqualTo(new ItemScore(null, null, null, null));
    }
}
