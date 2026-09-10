package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository
import kr.ac.kookmin.familyfitness.identity.domain.Cheer
import kr.ac.kookmin.familyfitness.identity.domain.Family
import kr.ac.kookmin.familyfitness.identity.domain.Profile
import kr.ac.kookmin.familyfitness.identity.domain.User
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

// application 서비스 테스트용 인메모리 포트 구현. 애그리게잇을 그대로 들고 있어 저장 뒤 같은 객체가 보인다.

class InMemoryFamilyRepository : FamilyRepository {
    val families = linkedMapOf<UUID, Family>()

    override fun allIds(): List<UUID> = families.keys.toList()

    /** 조건부 UPDATE 를 흉내낸다. 테스트가 `false` 로 바꾸면 "다른 계정이 먼저 가져간" 상황이 된다. */
    var attachSucceeds: Boolean = true
    val attachCalls = mutableListOf<Triple<UUID, UUID, Instant>>()

    override fun findById(familyId: UUID): Family? = families[familyId]

    override fun findByProfileId(profileId: UUID): Family? = families.values.firstOrNull { f -> f.profiles.any { it.id == profileId } }

    override fun findByClaimCode(code: String): Family? = families.values.firstOrNull { f -> f.profiles.any { it.claimCode?.code == code } }

    override fun isClaimCodeTaken(code: String): Boolean = findByClaimCode(code) != null

    override fun profilesOfUser(userId: UUID): List<Profile> = families.values.flatMap { f -> f.profiles.filter { it.userId == userId } }

    override fun save(family: Family): Family {
        families[family.id] = family
        return family
    }

    override fun attachUserIfUnclaimed(
        profileId: UUID,
        userId: UUID,
        at: Instant,
    ): Boolean {
        attachCalls += Triple(profileId, userId, at)
        if (!attachSucceeds) return false
        val profile = findByProfileId(profileId)?.profile(profileId) ?: return false
        if (profile.hasAccount) return false
        profile.claim(userId, at)
        return true
    }
}

class InMemoryUserRepository : UserRepository {
    val users = linkedMapOf<UUID, User>()

    override fun findById(id: UUID): User? = users[id]

    override fun findByProviderAndProviderUserId(
        provider: String,
        providerUserId: String,
    ): User? = users.values.firstOrNull { it.provider == provider && it.providerUserId == providerUserId }

    override fun save(user: User): User {
        users[user.id] = user
        return user
    }
}

class InMemoryCheerRepository : CheerRepository {
    val cheers = mutableListOf<Cheer>()

    override fun save(cheer: Cheer): Cheer {
        cheers += cheer
        return cheer
    }

    override fun countFromTo(
        fromProfileId: UUID,
        toProfileId: UUID,
        after: Instant,
    ): Int = cheers.count { it.fromProfileId == fromProfileId && it.toProfileId == toProfileId && it.createdAt.isAfter(after) }

    override fun countInFamily(
        familyId: UUID,
        from: Instant,
        to: Instant,
    ): Int = cheers.count { it.familyId == familyId && !it.createdAt.isBefore(from) && it.createdAt.isBefore(to) }
}

/** 움직일 수 있는 고정 시각. 테스트가 [instant] 를 바꾸면 [withZone] 으로 파생된 시계까지 같이 움직인다. */
class MutableClock private constructor(
    private val state: State,
    private val zone: ZoneId,
) : Clock() {
    constructor(instant: Instant, zone: ZoneId = ZoneId.of("Asia/Seoul")) : this(State(instant), zone)

    private class State(
        var instant: Instant,
    )

    var instant: Instant
        get() = state.instant
        set(value) {
            state.instant = value
        }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(state, zone)

    override fun instant(): Instant = state.instant

    fun identityClock(): IdentityClock = IdentityClock(this, zone)
}
