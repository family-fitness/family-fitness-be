package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeel;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** {@link MissionFeedbackRepository} 의 JPA 구현. */
@Repository
public class MissionFeedbackPersistenceAdapter implements MissionFeedbackRepository {
    private final MissionFeedbackJpaRepository jpa;

    public MissionFeedbackPersistenceAdapter(MissionFeedbackJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * 덮어쓰기 → 없으면 넣기 → 그사이 다른 요청이 먼저 넣었으면 다시 덮어쓰기. 먼저 읽고 넣으면 같은 요청 둘이 함께 「없음」 을 보고
     * 하나가 기본 키 위반으로 끝나므로, 넣기는 충돌을 DB 가 삼키게 한다.
     */
    @Override
    public void upsert(MissionFeedback feedback) {
        String feel = feedback.feel().name();
        if (jpa.updateFeel(feedback.missionId(), feedback.profileId(), feel, feedback.createdAt()) > 0) return;
        if (jpa.insertIfAbsent(feedback.missionId(), feedback.profileId(), feel, feedback.createdAt()) > 0) return;
        jpa.updateFeel(feedback.missionId(), feedback.profileId(), feel, feedback.createdAt());
    }

    @Override
    public @Nullable MissionFeedback find(UUID missionId, UUID profileId) {
        return jpa.findById(new MissionFeedbackId(missionId, profileId))
                .map(it -> new MissionFeedback(
                        it.getId().getMissionId(),
                        it.getId().getProfileId(),
                        MissionFeel.valueOf(it.getFeel()),
                        it.getCreatedAt()))
                .orElse(null);
    }

    @Override
    public void deleteByMission(UUID missionId) {
        jpa.deleteByMission(missionId);
    }
}
