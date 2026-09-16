package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardianConsentTest {
    private final LocalDate today = LocalDate.of(2026, 9, 8);

    @Test
    @DisplayName("만 14세 미만만 보호자 동의가 필요하다")
    void 만_14세_미만만_보호자_동의가_필요하다() {
        assertThat(GuardianConsent.isRequired(today.minusYears(14).plusDays(1), today))
                .isTrue();
        assertThat(GuardianConsent.isRequired(today.minusYears(14), today)).isFalse();
        assertThat(GuardianConsent.isRequired(today.minusYears(40), today)).isFalse();
    }

    @Test
    @DisplayName("개인정보와 건강정보 둘 다 동의해야 완전한 동의다")
    void 개인정보와_건강정보_둘_다_동의해야_완전한_동의다() {
        assertThat(new GuardianConsent(true, true).isComplete()).isTrue();
        assertThat(new GuardianConsent(true, false).isComplete()).isFalse();
        assertThat(new GuardianConsent(false, true).isComplete()).isFalse();
        assertThat(new GuardianConsent(false, false).isComplete()).isFalse();
    }
}
