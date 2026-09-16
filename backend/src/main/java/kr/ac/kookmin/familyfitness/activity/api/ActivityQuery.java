package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.UUID;

public interface ActivityQuery {
    /** {@code from}~{@code to} (양끝 포함) 합계. */
    ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to);

    /** 그날 모든 출처의 활동 분 합계. */
    int activeMinutesOn(UUID profileId, LocalDate activityDate);
}
