package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ActivityQuery {
    /** {@code from}~{@code to} (양끝 포함) 합계. */
    ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to);

    /** 그날 모든 출처의 활동 분 합계. */
    int activeMinutesOn(UUID profileId, LocalDate activityDate);

    /**
     * {@code from}~{@code to}(양끝 포함) 안에서 서버가 잰 분(TIMER + VIDEO)이 0 보다 큰 날과 그날 분. 날짜 오름차순.
     * 걸음수(MANUAL)는 사람이 말한 값이라 세지 않는다. 이어서 한 날 · 업적이 「움직인 날」 로 쓴다.
     */
    List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to);

    /** 지금까지 서버가 잰 분이 있는 날 수와 분 합. 기간 제한이 없다 — 줄지 않는 값(FE 요청서 7장)이다. */
    VerifiedSummary verifiedSummary(UUID profileId);
}
