package kr.ac.kookmin.familyfitness.progress.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;

/** 받은 업적. 넣기와 읽기만 있다 — 한 번 받은 업적은 지우지 않는다(FE 요청서 7장 「줄지 않는 값」). */
public interface AchievementStore {
    /** 이 사람이 받은 업적과 처음 받은 시각. */
    Map<Achievement, Instant> earnedOf(UUID profileId);

    /** 처음이면 넣고 true. 이미 받았으면 그대로 두고 false — 처음 받은 시각을 바꾸지 않는다. */
    boolean grant(UUID profileId, Achievement achievement, Instant earnedAt);
}
