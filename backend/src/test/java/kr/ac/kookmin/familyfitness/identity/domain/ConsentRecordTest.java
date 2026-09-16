package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsentRecordTest {
    private final Instant at = Instant.parse("2026-09-08T10:00:00Z");
    private final UUID by = UUID.randomUUID();

    @Test
    @DisplayName("동의 기록은 두 시각이 있고 철회되지 않았을 때만 유효하다")
    void 동의_기록은_두_시각이_있고_철회되지_않았을_때만_유효하다() {
        assertThat(ConsentRecord.NONE.isGiven()).isFalse();
        ConsentRecord granted = ConsentRecord.granted(at, by);
        assertThat(granted.isGiven()).isTrue();
        assertThat(granted.personalAt()).isEqualTo(at);
        assertThat(granted.healthAt()).isEqualTo(at);
        assertThat(granted.byUserId()).isEqualTo(by);

        ConsentRecord revoked = granted.revoke(at.plusSeconds(1));
        assertThat(revoked.isGiven()).isFalse();
        assertThat(revoked.personalAt()).isEqualTo(at);
        assertThat(revoked.revokedAt()).isEqualTo(at.plusSeconds(1));
    }
}
