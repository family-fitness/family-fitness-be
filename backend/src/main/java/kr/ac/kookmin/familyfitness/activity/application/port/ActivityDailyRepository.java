package kr.ac.kookmin.familyfitness.activity.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.VerifiedSummary;
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord;
import org.jspecify.annotations.Nullable;

public interface ActivityDailyRepository {
    @Nullable
    DailyActivityRecord find(UUID profileId, LocalDate activityDate, ActivitySource source);

    DailyActivityRecord save(DailyActivityRecord record);

    /** {@code from}~{@code to} 양끝 포함 합계. */
    ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to);

    int activeMinutesOn(UUID profileId, LocalDate activityDate);

    /** 이 프로필들 중 누구라도 그날 운동한 초(TIMER · VIDEO)가 있는가. MANUAL(걸음수) 행은 초가 늘 0 이라 세지 않는다. */
    boolean anyActiveOn(Collection<UUID> profileIds, LocalDate activityDate);

    /** {@code from}~{@code to} 양끝 포함, 서버가 잰 초(TIMER · VIDEO)가 0 보다 큰 날과 그날 분(내림). 날짜 오름차순. */
    List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to);

    /** {@link #verifiedDays} 를 여러 프로필에 쿼리 한 번으로. 요청한 프로필마다 한 줄이고, 움직인 날이 없으면 빈 목록이다. */
    Map<UUID, List<DailyMinutes>> verifiedDaysOf(Collection<UUID> profileIds, LocalDate from, LocalDate to);

    /** 기간 제한 없이 서버가 잰 초가 있는 날 수와 분 합(초 합 내림). */
    VerifiedSummary verifiedSummary(UUID profileId);
}
