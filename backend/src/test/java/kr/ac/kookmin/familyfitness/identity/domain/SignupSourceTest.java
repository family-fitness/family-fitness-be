package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupSourceTest {
    @Test
    @DisplayName("앞뒤 공백과 제어 문자를 걷고, 빈 값은 null 로 둔다")
    void 공백과_제어_문자를_걷는다() {
        SignupSource source = SignupSource.of("  instagram \n", "\t", "체력 나이\r\n");

        assertThat(source.source()).isEqualTo("instagram");
        assertThat(source.medium()).isNull();
        assertThat(source.campaign()).isEqualTo("체력 나이");
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("64자를 넘으면 자른다")
    void 길면_자른다() {
        SignupSource source = SignupSource.of("a".repeat(100), null, null);

        assertThat(source.source()).hasSize(SignupSource.MAX_LENGTH);
    }

    @Test
    @DisplayName("셋 다 비었으면 비어 있는 유입 경로다")
    void 셋_다_비면_비어_있다() {
        assertThat(SignupSource.of(null, " ", "").isEmpty()).isTrue();
        assertThat(SignupSource.NONE.isEmpty()).isTrue();
    }
}
