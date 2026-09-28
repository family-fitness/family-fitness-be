package kr.ac.kookmin.familyfitness.activity.application.port;

import java.time.LocalDate;
import java.util.Collection;
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

    /** 이 프로필들 중 누구라도 그날 운동한 분(TIMER · VIDEO)이 있는가. MANUAL(걸음수) 행은 분이 늘 0 이라 세지 않는다. */
    boolean anyActiveOn(Collection<UUID> profileIds, LocalDate activityDate);
}
