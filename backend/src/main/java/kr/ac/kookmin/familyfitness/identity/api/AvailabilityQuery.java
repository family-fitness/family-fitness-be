package kr.ac.kookmin.familyfitness.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * 다른 모듈이 프로필의 「운동할 수 있는 시간」 을 읽는 통로. AI 편성의 「몇 분」 기본값 같은 데 쓴다.
 * 운동을 막는 데 쓰지 않는다 — 적어 둔 날이 아니어도 운동은 된다. 권한 판단은 부르는 쪽이 한다.
 */
public interface AvailabilityQuery {
    /** 요일 차례(월 → 일), 하루 한 칸. 적어 둔 것이 없거나 없는 프로필이면 빈 목록. */
    List<AvailabilitySlot> slotsOf(UUID profileId);
}
