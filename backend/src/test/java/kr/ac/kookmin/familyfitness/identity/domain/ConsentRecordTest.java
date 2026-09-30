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

    @Test
    @DisplayName("거둔 채인지는 철회 시각으로 본다 — 동의한 적 없이 거둬도 거둔 채고, 다시 동의하면 풀린다")
    void 거둔_채인지는_철회_시각으로_본다() {
        assertThat(ConsentRecord.NONE.isRevoked()).isFalse();
        assertThat(ConsentRecord.granted(at, by).isRevoked()).isFalse();
        assertThat(ConsentRecord.NONE.revoke(at).isRevoked()).isTrue();

        ConsentRecord revoked = ConsentRecord.granted(at, by).revoke(at.plusSeconds(1));
        assertThat(revoked.isRevoked()).isTrue();
        assertThat(ConsentRecord.granted(at.plusSeconds(2), by).isRevoked()).isFalse();
    }
}
