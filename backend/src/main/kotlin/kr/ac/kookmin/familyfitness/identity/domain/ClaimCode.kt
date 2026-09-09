package kr.ac.kookmin.familyfitness.identity.domain

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * 초대 코드 값 객체. 6자리 대문자+숫자이며 헷갈리는 0/O·1/I 는 쓰지 않는다. 발급 후 7일에 만료.
 * 재발급하면 이전 코드는 즉시 무효다([Profile.issueInvite]).
 */
data class ClaimCode(
    val code: String,
    val expiresAt: Instant,
) {
    init {
        require(code.length == LENGTH && code.all { it in ALPHABET }) { "초대 코드 형식이 아니다: $code" }
    }

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    companion object {
        const val LENGTH = 6
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val TTL: Duration = Duration.ofDays(7)

        fun generate(
            now: Instant,
            random: SecureRandom,
        ): ClaimCode {
            val chars = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
            return ClaimCode(String(chars), now.plus(TTL))
        }

        /** 사용자가 입력한 코드는 대소문자를 가리지 않는다. */
        fun normalize(raw: String): String = raw.trim().uppercase()
    }
}
