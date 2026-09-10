package kr.ac.kookmin.familyfitness.shared.domain

import java.security.MessageDigest
import java.util.UUID

/**
 * AI 서비스에 넘기는 불투명 프로필 참조. 이름·생년월일·계정 식별자를 담지 않는다.
 * 같은 프로필은 항상 같은 ref 가 나오며(UUID 의 SHA-256 앞 16자), 역매핑은 요청을 보낸 서버가 [indexOf] 로 보관한다.
 * UUID 앞부분을 자르면 시드처럼 규칙적인 ID 끼리 충돌하므로 해시를 쓴다.
 */
object ProfileRef {
    fun of(profileId: UUID): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(profileId.toString().toByteArray(Charsets.UTF_8))
        return "p_" + digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun indexOf(profileIds: Collection<UUID>): Map<String, UUID> = profileIds.associateBy { of(it) }
}
