package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.application.port.AchievementStore;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link AchievementStore} 의 JPA 구현. */
@Repository
@Transactional(readOnly = true)
public class AchievementStoreAdapter implements AchievementStore {
    private final AchievementJpaRepository jpa;

    public AchievementStoreAdapter(AchievementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<Achievement, Instant> earnedOf(UUID profileId) {
        Map<Achievement, Instant> earned = new EnumMap<>(Achievement.class);
        jpa.findByIdProfileId(profileId)
                .forEach(it -> earned.put(Achievement.valueOf(it.getId().getCode()), it.getEarnedAt()));
        return earned;
    }

    /** 있는지 먼저 본다. 동시에 같은 업적을 넣으면 늦은 쪽이 기본 키(profile_id, code)에 걸린다. */
    @Override
    @Transactional
    public boolean grant(UUID profileId, Achievement achievement, Instant earnedAt) {
        AchievementId id = new AchievementId(profileId, achievement.code());
        if (jpa.existsById(id)) return false;
        jpa.save(new AchievementEntity(id, earnedAt));
        return true;
    }
}
