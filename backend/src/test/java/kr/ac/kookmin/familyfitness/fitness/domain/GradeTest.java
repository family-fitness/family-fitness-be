package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ac.kookmin.familyfitness.shared.domain.Band;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradeTest {
    @Test
    @DisplayName("등급 와이어 값은 1등급 · 2등급 · 3등급 · 참가 넷뿐이다(FE Grade 타입과 같다)")
    void 등급_와이어_값은_넷뿐이다() {
        assertThat(Grade.values()).extracting(Grade::getLabel).containsExactly("1등급", "2등급", "3등급", "참가");
        assertThat(Grade.fromLabel("참가")).isEqualTo(Grade.PARTICIPATION);
        assertThat(Grade.fromLabel("2등급")).isEqualTo(Grade.SECOND);
        assertThatThrownBy(() -> Grade.fromLabel("4등급")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("구간 · 상위 문구는 백분위에서, 등급은 공식 기준표에서 따로 온다")
    void 구간_상위_문구는_백분위에서_등급은_기준표에서_따로_온다() {
        ItemScore score = ItemScore.of(24, Grade.THIRD);
        assertThat(score.grade()).isEqualTo(Grade.THIRD);
        assertThat(score.band()).isEqualTo(Band.GROWTH);
        assertThat(score.topPercentText()).isEqualTo("상위 76%");

        // 백분위가 높아도 기준표에 못 미치면 참가다(예전 85/65/40 규칙이면 2등급)
        ItemScore strong = ItemScore.of(75, Grade.PARTICIPATION);
        assertThat(strong.grade()).isEqualTo(Grade.PARTICIPATION);
        assertThat(strong.band()).isEqualTo(Band.STRENGTH);
        assertThat(strong.topPercentText()).isEqualTo("상위 25%");
    }

    @Test
    @DisplayName("AI 백분위는 0 과 100 도 나온다 — 100 은 「상위 0%」 가 아니라 「상위 1%」")
    void 백분위_100_은_상위_1퍼센트다() {
        assertThat(ItemScore.of(100, null)).isEqualTo(new ItemScore(100, null, Band.STRENGTH, "상위 1%"));
        assertThat(ItemScore.of(99, null).topPercentText()).isEqualTo("상위 1%");
        assertThat(ItemScore.of(0, null)).isEqualTo(new ItemScore(0, null, Band.GROWTH, "상위 100%"));
    }

    @Test
    @DisplayName("규준이 없으면 백분위 · 구간 · 문구는 null 이고 등급은 기준표대로 남는다")
    void 규준이_없으면_백분위_쪽만_null() {
        assertThat(ItemScore.of(null, Grade.SECOND)).isEqualTo(new ItemScore(null, Grade.SECOND, null, null));
        assertThat(ItemScore.of(null, null)).isEqualTo(ItemScore.NONE);
        assertThat(ItemScore.of(50, null)).isEqualTo(new ItemScore(50, null, Band.STEADY, "상위 50%"));
    }
}
