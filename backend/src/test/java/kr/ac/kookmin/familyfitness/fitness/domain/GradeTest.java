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
    @DisplayName("항목 점수는 백분위에서 구간 · 상위 문구만 파생한다 — 항목마다 등급은 없다")
    void 항목_점수는_백분위에서_구간과_상위_문구만_파생한다() {
        assertThat(ItemScore.of(24)).isEqualTo(new ItemScore(24, Band.GROWTH, "상위 76%"));
        assertThat(ItemScore.of(75)).isEqualTo(new ItemScore(75, Band.STRENGTH, "상위 25%"));
        assertThat(ItemScore.of(50)).isEqualTo(new ItemScore(50, Band.STEADY, "상위 50%"));
    }

    @Test
    @DisplayName("AI 백분위는 0 과 100 도 나온다 — 100 은 「상위 0%」 가 아니라 「상위 1%」")
    void 백분위_100_은_상위_1퍼센트다() {
        assertThat(ItemScore.of(100)).isEqualTo(new ItemScore(100, Band.STRENGTH, "상위 1%"));
        assertThat(ItemScore.of(99).topPercentText()).isEqualTo("상위 1%");
        assertThat(ItemScore.of(0)).isEqualTo(new ItemScore(0, Band.GROWTH, "상위 100%"));
    }

    @Test
    @DisplayName("또래 분포가 없으면 백분위 · 구간 · 문구가 모두 null")
    void 또래_분포가_없으면_모두_null() {
        assertThat(ItemScore.of(null)).isEqualTo(ItemScore.NONE);
        assertThat(ItemScore.NONE).isEqualTo(new ItemScore(null, null, null));
    }
}
