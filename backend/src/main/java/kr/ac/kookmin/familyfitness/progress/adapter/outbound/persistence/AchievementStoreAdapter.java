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

    /**
     * 받은 적 없을 때만 넣는다 — 판단은 DB 의 ON CONFLICT DO NOTHING 이 한다({@link AchievementJpaRepository#insertIfAbsent}).
     * 동시에 같은 업적을 넣어도 늦은 쪽은 false 이고 예외가 없다(QA SA-12). false 면 업적 이벤트도 내지 않는다(AchievementAwards).
     */
    @Override
    @Transactional
    public boolean grant(UUID profileId, Achievement achievement, Instant earnedAt) {
        return jpa.insertIfAbsent(profileId, achievement.code(), earnedAt) == 1;
    }
}
