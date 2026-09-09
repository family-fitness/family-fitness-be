package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.domain.Cheer
import kr.ac.kookmin.familyfitness.identity.domain.TooManyCheersException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 가족 안의 응원. 규칙은 [kr.ac.kookmin.familyfitness.identity.domain.Family.validateCheer], 과다 호출은 저장된 건수로 판정. */
@Service
@Transactional
class CheerService(
    private val families: FamilyRepository,
    private val cheers: CheerRepository,
    private val clock: IdentityClock,
) {
    fun cheer(
        userId: UUID,
        familyId: UUID,
        fromProfileId: UUID,
        toProfileId: UUID,
        message: String?,
        emoji: String?,
        missionId: UUID?,
    ): Cheer {
        val family = families.findById(familyId) ?: throw FamilyNotFoundException(familyId)
        family.validateCheer(userId, fromProfileId, toProfileId)
        val now = clock.now()
        if (cheers.countFromTo(fromProfileId, toProfileId, now.minus(Cheer.WINDOW)) >= Cheer.MAX_PER_WINDOW) {
            throw TooManyCheersException()
        }
        return cheers.save(
            Cheer(
                id = UUID.randomUUID(),
                familyId = familyId,
                fromProfileId = fromProfileId,
                toProfileId = toProfileId,
                message = message?.takeIf { it.isNotBlank() },
                emoji = emoji?.takeIf { it.isNotBlank() },
                missionId = missionId,
                createdAt = now,
            ),
        )
    }
}
