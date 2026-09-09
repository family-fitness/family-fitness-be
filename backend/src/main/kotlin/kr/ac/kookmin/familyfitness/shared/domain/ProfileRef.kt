package kr.ac.kookmin.familyfitness.shared.domain

import java.util.UUID

/**
 * AI 서비스에 넘기는 불투명 프로필 참조. 이름·생년월일·계정 식별자를 담지 않는다.
 * 같은 프로필은 항상 같은 ref 가 나오며, 역매핑은 요청을 보낸 서버가 보관한다.
 */
object ProfileRef {
    fun of(profileId: UUID): String = "p_" + profileId.toString().replace("-", "").take(12)

    fun indexOf(profileIds: Collection<UUID>): Map<String, UUID> = profileIds.associateBy { of(it) }
}
