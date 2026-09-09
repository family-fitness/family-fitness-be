package kr.ac.kookmin.familyfitness.identity.application.port

import kr.ac.kookmin.familyfitness.identity.domain.Cheer
import java.time.Instant
import java.util.UUID

interface CheerRepository {
    fun save(cheer: Cheer): Cheer

    /** 같은 보내는 쪽→받는 쪽 조합으로 [after] 보다 뒤(초과)에 보낸 횟수(과다 호출 판정). */
    fun countFromTo(
        fromProfileId: UUID,
        toProfileId: UUID,
        after: Instant,
    ): Int

    /** [from, to) 구간의 가족 응원 수. */
    fun countInFamily(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Int
}
