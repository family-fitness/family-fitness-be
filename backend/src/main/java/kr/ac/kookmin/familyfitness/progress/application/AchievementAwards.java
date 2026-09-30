package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import kr.ac.kookmin.familyfitness.progress.application.port.AchievementStore;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 업적 주기. 저장소에 처음 들어간 업적만 {@link AchievementEarned} 로 알린다 — 같은 업적을 다시 판정해도 알림이 두 번 가지 않는다.
 * 부르는 쪽(칸 끝 · 응원 · 측정)의 트랜잭션 안에서 발행한다.
 */
@Component
public class AchievementAwards {
    private final AchievementStore store;
    private final ApplicationEventPublisher events;

    public AchievementAwards(AchievementStore store, ApplicationEventPublisher events) {
        this.store = store;
        this.events = events;
    }

    /** 처음이면 넣고 이벤트를 낸 뒤 true. 이미 받았으면 아무것도 하지 않고 false. */
    public boolean grant(UUID profileId, Achievement achievement, Instant earnedAt) {
        if (!store.grant(profileId, achievement, earnedAt)) return false;
        events.publishEvent(new AchievementEarned(
                profileId, achievement.code(), achievement.title(), achievement.description(), earnedAt));
        return true;
    }
}
