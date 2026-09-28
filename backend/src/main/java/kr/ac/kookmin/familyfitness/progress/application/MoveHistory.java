package kr.ac.kookmin.familyfitness.progress.application;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDays;
import kr.ac.kookmin.familyfitness.progress.domain.MoveWindow;
import kr.ac.kookmin.familyfitness.progress.domain.Streak;
import org.springframework.stereotype.Component;

/**
 * 이어서 한 날 셈에 드는 세 가지를 모은다.
 *
 * <pre>
 * 움직인 날  activity.api — 서버가 잰 분(TIMER · VIDEO)이 0 보다 큰 날
 * 쉬는 날    activity.api.RestDayQuery — 가족 단위
 * 잡힌 날    progress.api.PlannedDays — coaching 이 구현한다
 * </pre>
 */
@Component
public class MoveHistory {
    private final ActivityQuery activity;
    private final RestDayQuery restDays;
    private final PlannedDays plannedDays;

    public MoveHistory(ActivityQuery activity, RestDayQuery restDays, PlannedDays plannedDays) {
        this.activity = activity;
        this.restDays = restDays;
        this.plannedDays = plannedDays;
    }

    /** {@code today} 부터 거꾸로 {@link Streak#MAX_DAYS} 일의 창. */
    public MoveWindow load(UUID profileId, UUID familyId, LocalDate today) {
        LocalDate from = Streak.windowStart(today);
        return new MoveWindow(
                today,
                movedDays(profileId, from, today),
                new HashSet<>(restDays.restDaysBetween(familyId, from, today)),
                plannedDays.plannedDays(profileId, from, today));
    }

    /** {@code from}~{@code to}(양끝 포함) 안의 움직인 날. */
    public Set<LocalDate> movedDays(UUID profileId, LocalDate from, LocalDate to) {
        return activity.verifiedDays(profileId, from, to).stream()
                .map(DailyMinutes::date)
                .collect(Collectors.toSet());
    }
}
