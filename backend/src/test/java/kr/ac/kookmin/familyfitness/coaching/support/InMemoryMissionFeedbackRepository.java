package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import org.jspecify.annotations.Nullable;

/** 운동 느낌의 메모리 저장소. (미션, 사람) 한 줄, 다시 넣으면 덮어쓴다. */
public class InMemoryMissionFeedbackRepository implements MissionFeedbackRepository {
    public record Key(UUID missionId, UUID profileId) {}

    public final Map<Key, MissionFeedback> rows = new ConcurrentHashMap<>();

    @Override
    public void upsert(MissionFeedback feedback) {
        rows.put(new Key(feedback.missionId(), feedback.profileId()), feedback);
    }

    @Override
    public @Nullable MissionFeedback find(UUID missionId, UUID profileId) {
        return rows.get(new Key(missionId, profileId));
    }

    @Override
    public void deleteByMission(UUID missionId) {
        rows.keySet().removeIf(it -> it.missionId().equals(missionId));
    }
}
