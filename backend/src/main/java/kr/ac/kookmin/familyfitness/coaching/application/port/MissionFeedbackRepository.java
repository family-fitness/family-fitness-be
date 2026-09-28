package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import org.jspecify.annotations.Nullable;

/** 운동 느낌(mission_feedback). 한 사람 · 한 미션에 한 줄. */
public interface MissionFeedbackRepository {
    /** 없으면 넣고 있으면 느낌 · 시각을 덮어쓴다. 같은 줄 요청 둘이 동시에 와도 기본 키 위반을 내지 않는다. */
    void upsert(MissionFeedback feedback);

    @Nullable
    MissionFeedback find(UUID missionId, UUID profileId);

    /** 미션을 지울 때 그 미션의 느낌을 모두 지운다. */
    void deleteByMission(UUID missionId);
}
