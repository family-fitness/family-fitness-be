package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import org.jspecify.annotations.Nullable;

/** 칸 끝 기록(mission_session_completions). 넣기만 하고 고치거나 지우지 않는다. */
public interface SessionCompletionRepository {
    @Nullable
    SessionCompletion find(UUID missionId, int position, UUID profileId);

    /**
     * 한 행을 넣고 곧바로 DB 에 내보낸다. 같은 (미션, 칸, 사람)이 이미 있으면 기본 키에 걸려
     * {@link org.springframework.dao.DataIntegrityViolationException} 이 난다 — 부르는 쪽이 먼저 {@link #find} 로 본다.
     * 부르는 쪽이 미션을 읽은 뒤 그 미션이 지워졌으면(보호자의 지우기가 먼저 커밋) 미션 외래 키에 걸리고, 이것은
     * {@link kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException}(404)으로 바꿔 던진다.
     */
    void insert(SessionCompletion completion);

    List<SessionCompletion> findByMission(UUID missionId);

    List<SessionCompletion> findByMissions(Collection<UUID> missionIds);
}
