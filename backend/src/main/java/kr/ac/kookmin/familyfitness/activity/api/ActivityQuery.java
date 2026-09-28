package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 활동 읽기. 분은 모두 초 합을 60 으로 나눠 내림한 값이다(결정 37). 「움직인 날」 은 그날 서버가 잰 초(TIMER + VIDEO)가
 * 0 보다 큰 날이다 — 40초만 한 날도 움직인 날이다(분으로는 0).
 */
public interface ActivityQuery {
    /** {@code from}~{@code to} (양끝 포함) 합계. */
    ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to);

    /** 그날 모든 출처의 활동 분 합계. */
    int activeMinutesOn(UUID profileId, LocalDate activityDate);

    /**
     * {@code from}~{@code to}(양끝 포함) 안에서 서버가 잰 초(TIMER + VIDEO)가 0 보다 큰 날과 그날 분. 날짜 오름차순.
     * 걸음수(MANUAL)는 사람이 말한 값이라 세지 않는다. 이어서 한 날 · 업적 · 쉬는 날(ALREADY_MOVED)이 「움직인 날」 로 쓴다.
     */
    List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to);

    /** 지금까지 서버가 잰 초가 있는 날 수와 분 합. 기간 제한이 없다 — 줄지 않는 값(FE 요청서 7장)이다. */
    VerifiedSummary verifiedSummary(UUID profileId);

    /**
     * 여러 프로필의 {@link #verifiedDays} 를 한 번에 읽는다(리그 방처럼 여러 아이를 같이 셀 때). 요청한 프로필마다 한 줄이고,
     * 움직인 날이 없으면 빈 목록이다. 기본 구현은 프로필마다 {@link #verifiedDays} 를 부른다.
     */
    default Map<UUID, List<DailyMinutes>> verifiedDaysOf(Collection<UUID> profileIds, LocalDate from, LocalDate to) {
        Map<UUID, List<DailyMinutes>> out = new LinkedHashMap<>();
        for (UUID profileId : profileIds) out.put(profileId, verifiedDays(profileId, from, to));
        return Collections.unmodifiableMap(out);
    }
}
