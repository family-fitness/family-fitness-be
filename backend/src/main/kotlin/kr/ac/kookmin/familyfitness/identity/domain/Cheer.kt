package kr.ac.kookmin.familyfitness.identity.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 가족 안의 응원 한 건. 별도 애그리게잇이며 보내는 쪽·받는 쪽은 프로필 ID 로만 가리킨다. */
data class Cheer(
    val id: UUID,
    val familyId: UUID,
    val fromProfileId: UUID,
    val toProfileId: UUID,
    val message: String?,
    val emoji: String?,
    val missionId: UUID?,
    val createdAt: Instant,
) {
    init {
        require(fromProfileId != toProfileId) { "자기 자신에게는 보낼 수 없다" }
        require(!message.isNullOrBlank() || !emoji.isNullOrBlank()) { "메시지나 이모지 중 하나는 있어야 한다" }
    }

    companion object {
        /** 같은 대상에게 분당 이 횟수를 넘기면 `TOO_MANY`. */
        const val MAX_PER_WINDOW = 5
        val WINDOW: Duration = Duration.ofMinutes(1)
    }
}
