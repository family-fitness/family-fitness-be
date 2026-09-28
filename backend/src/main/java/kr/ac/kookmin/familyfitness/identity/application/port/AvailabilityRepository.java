package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability;

/** 프로필마다 한 주 「운동할 수 있는 시간」 저장소. */
public interface AvailabilityRepository {
    /** 요일 차례(월 → 일). 없으면 빈 목록. */
    List<AvailabilitySlot> findByProfileId(UUID profileId);

    /**
     * 그 프로필의 한 주를 통째로 바꾼다 — 있던 칸을 모두 지우고 {@code week} 를 넣는다. 부르는 쪽 트랜잭션 안에서 돈다.
     * 같은 프로필을 두 요청이 동시에 바꾸면 차례대로 처리해, 두 요청의 칸이 섞이지 않고 나중 요청의 한 주가 남는다.
     */
    void replace(UUID profileId, WeeklyAvailability week, UUID savedBy, Instant savedAt);
}
