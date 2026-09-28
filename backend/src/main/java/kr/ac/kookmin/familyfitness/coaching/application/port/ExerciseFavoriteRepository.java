package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 프로필마다 찜한 운동 구간(clipId). 행 하나 = (프로필, 클립) 한 쌍이고, 찜을 풀면 행을 지운다. */
public interface ExerciseFavoriteRepository {
    /** 이 프로필이 찜한 clipId 전부. 꺼진 클립(active=false)의 찜도 담는다 — 거르는 것은 부르는 쪽이 한다. */
    Set<String> clipIdsOf(UUID profileId);

    /** 찜을 더한다. 이미 있으면 그대로 둔다(처음 찜한 시각을 지킨다). 같은 요청이 동시에 와도 한 행만 남는다. */
    void add(UUID profileId, String clipId, Instant at);

    /** 찜을 뺀다. 없으면 아무것도 하지 않는다. */
    void remove(UUID profileId, String clipId);
}
