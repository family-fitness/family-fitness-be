package kr.ac.kookmin.familyfitness.activity.application.port;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord;
import org.jspecify.annotations.Nullable;

public interface ActivityDailyRepository {
    @Nullable
    DailyActivityRecord find(UUID profileId, LocalDate activityDate, ActivitySource source);

    DailyActivityRecord save(DailyActivityRecord record);

    /** {@code from}~{@code to} 양끝 포함 합계. */
    ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to);

    int activeMinutesOn(UUID profileId, LocalDate activityDate);
}
