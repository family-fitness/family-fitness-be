package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClaimCodeTest {
    private final Instant now = Instant.parse("2026-09-08T10:00:00Z");

    @Test
    @DisplayName("여섯 자리이고 0 O 1 I 를 쓰지 않으며 7일 뒤 만료된다")
    void 여섯_자리이고_0_O_1_I_를_쓰지_않으며_7일_뒤_만료된다() {
        for (int i = 0; i < 200; i++) {
            ClaimCode code = ClaimCode.generate(now, new SecureRandom());
            assertThat(code.code()).hasSize(6);
            assertThat(code.code()).matches("[A-HJ-NP-Z2-9]{6}");
            assertThat(code.expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        }
    }

    @Test
    @DisplayName("만료 시각부터 만료로 본다")
    void 만료_시각부터_만료로_본다() {
        ClaimCode code = new ClaimCode("ABC234", now.plus(Duration.ofDays(7)));
        assertThat(code.isExpired(now)).isFalse();
        assertThat(code.isExpired(code.expiresAt().minusSeconds(1))).isFalse();
        assertThat(code.isExpired(code.expiresAt())).isTrue();
    }

    @Test
    @DisplayName("입력 코드는 공백을 걷어내고 대문자로 맞춘다")
    void 입력_코드는_공백을_걷어내고_대문자로_맞춘다() {
        assertThat(ClaimCode.normalize(" abc234 ")).isEqualTo("ABC234");
    }

    @Test
    @DisplayName("형식이 어긋난 코드는 만들 수 없다")
    void 형식이_어긋난_코드는_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimCode("ABC0IO", now));
        assertThrows(IllegalArgumentException.class, () -> new ClaimCode("ABC23", now));
        assertThrows(IllegalArgumentException.class, () -> new ClaimCode("abc234", now));
    }
}
