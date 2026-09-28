package kr.ac.kookmin.familyfitness.coaching.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

/** 칸 끝 기록의 메모리 저장소. 같은 (미션, 칸, 사람)을 두 번 넣으면 DB 처럼 기본 키 위반을 던진다. */
public class InMemorySessionCompletionRepository implements SessionCompletionRepository {
    public record Key(UUID missionId, int position, UUID profileId) {}

    public final Map<Key, SessionCompletion> rows = new ConcurrentHashMap<>();

    @Override
    public @Nullable SessionCompletion find(UUID missionId, int position, UUID profileId) {
        return rows.get(new Key(missionId, position, profileId));
    }

    @Override
    public void insert(SessionCompletion completion) {
        Key key = new Key(completion.missionId(), completion.position(), completion.profileId());
        if (rows.putIfAbsent(key, completion) != null) {
            throw new DataIntegrityViolationException("pk_mission_session_completions: " + key);
        }
    }

    @Override
    public List<SessionCompletion> findByMission(UUID missionId) {
        return rows.values().stream()
                .filter(it -> it.missionId().equals(missionId))
                .toList();
    }

    @Override
    public List<SessionCompletion> findByMissions(Collection<UUID> missionIds) {
        return rows.values().stream()
                .filter(it -> missionIds.contains(it.missionId()))
                .toList();
    }

    /** 한 사람의 기록. */
    public List<SessionCompletion> of(UUID profileId) {
        return rows.values().stream()
                .filter(it -> it.profileId().equals(profileId))
                .toList();
    }
}
