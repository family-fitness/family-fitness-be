package kr.ac.kookmin.familyfitness.identity.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GuardianConsentTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `만 14세 미만만 보호자 동의가 필요하다`() {
        assertThat(GuardianConsent.isRequired(today.minusYears(14).plusDays(1), today)).isTrue()
        assertThat(GuardianConsent.isRequired(today.minusYears(14), today)).isFalse()
        assertThat(GuardianConsent.isRequired(today.minusYears(40), today)).isFalse()
    }

    @Test
    fun `개인정보와 건강정보 둘 다 동의해야 완전한 동의다`() {
        assertThat(GuardianConsent(personalData = true, healthData = true).isComplete).isTrue()
        assertThat(GuardianConsent(personalData = true, healthData = false).isComplete).isFalse()
        assertThat(GuardianConsent(personalData = false, healthData = true).isComplete).isFalse()
        assertThat(GuardianConsent(personalData = false, healthData = false).isComplete).isFalse()
    }
}

class ConsentRecordTest {
    private val at = Instant.parse("2026-09-08T10:00:00Z")
    private val by = UUID.randomUUID()

    @Test
    fun `동의 기록은 두 시각이 있고 철회되지 않았을 때만 유효하다`() {
        assertThat(ConsentRecord.NONE.isGiven).isFalse()
        val granted = ConsentRecord.granted(at, by)
        assertThat(granted.isGiven).isTrue()
        assertThat(granted.personalAt).isEqualTo(at)
        assertThat(granted.healthAt).isEqualTo(at)
        assertThat(granted.byUserId).isEqualTo(by)

        val revoked = granted.revoke(at.plusSeconds(1))
        assertThat(revoked.isGiven).isFalse()
        assertThat(revoked.personalAt).isEqualTo(at)
        assertThat(revoked.revokedAt).isEqualTo(at.plusSeconds(1))
    }
}

class ClaimCodeTest {
    private val now = Instant.parse("2026-09-08T10:00:00Z")

    @Test
    fun `여섯 자리이고 0 O 1 I 를 쓰지 않으며 7일 뒤 만료된다`() {
        repeat(200) {
            val code = ClaimCode.generate(now, SecureRandom())
            assertThat(code.code).hasSize(6)
            assertThat(code.code).matches("[A-HJ-NP-Z2-9]{6}")
            assertThat(code.expiresAt).isEqualTo(now.plus(Duration.ofDays(7)))
        }
    }

    @Test
    fun `만료 시각부터 만료로 본다`() {
        val code = ClaimCode("ABC234", now.plus(Duration.ofDays(7)))
        assertThat(code.isExpired(now)).isFalse()
        assertThat(code.isExpired(code.expiresAt.minusSeconds(1))).isFalse()
        assertThat(code.isExpired(code.expiresAt)).isTrue()
    }

    @Test
    fun `입력 코드는 공백을 걷어내고 대문자로 맞춘다`() {
        assertThat(ClaimCode.normalize(" abc234 ")).isEqualTo("ABC234")
    }

    @Test
    fun `형식이 어긋난 코드는 만들 수 없다`() {
        assertThrows<IllegalArgumentException> { ClaimCode("ABC0IO", now) }
        assertThrows<IllegalArgumentException> { ClaimCode("ABC23", now) }
        assertThrows<IllegalArgumentException> { ClaimCode("abc234", now) }
    }
}
