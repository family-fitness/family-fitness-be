package kr.ac.kookmin.familyfitness.league.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate.ChildDays;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate.Result;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDaysSinceCreated;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 가족마다 그달 달성률(%)과 순위 점수를 센다. 재료는 다른 모듈의 공개 API 로 읽고, 셈은 {@link AchievementRate} 가 한다.
 *
 * <pre>
 * 셀 아이    identity.api.ProfileQuery#summariesOfFamilies — role CHILD. 보호자 동의가 필요한데 없는(거둔) 아이는 뺀다
 * 잡힌 날    progress.api.PlannedDaysSinceCreated — 미션이 선 날 중 만든 날(KST) 이후(coaching 구현)
 * 움직인 날  activity.api.ActivityQuery#verifiedDaysOf — 서버가 잰 활동(TIMER · VIDEO) &gt; 0 인 날(결정 37 「움직인 날」)
 * 쉬는 날    activity.api.RestDayQuery#restDaysOfFamilies — 가족 단위
 * </pre>
 *
 * 재료마다 방 전체(가족 · 아이 여럿)를 한 번에 부른다. 가족마다 · 아이마다 따로 부르면 열 가족 방 한 번 조회에 수십 번 DB 를 오간다.
 */
@Component
public class FamilyRates {
    private final ProfileQuery profiles;
    private final PlannedDaysSinceCreated plannedDays;
    private final ActivityQuery activity;
    private final RestDayQuery restDays;

    public FamilyRates(
            ProfileQuery profiles, PlannedDaysSinceCreated plannedDays, ActivityQuery activity, RestDayQuery restDays) {
        this.profiles = profiles;
        this.plannedDays = plannedDays;
        this.activity = activity;
        this.restDays = restDays;
    }

    /**
     * 가족마다 {@code month} 의 달성률 · 순위 점수. 셀 날이 없으면 그 가족의 값은 null 이다(0 이 아니다).
     * 이번 달이면 1일부터 오늘까지, 지난달이면 말일까지 센다.
     */
    public Map<UUID, @Nullable Result> of(Collection<UUID> familyIds, YearMonth month, LocalDate today) {
        LocalDate from = month.atDay(1);
        LocalDate until = month.atEndOfMonth().isBefore(today) ? month.atEndOfMonth() : today;
        Map<UUID, @Nullable Result> rates = new LinkedHashMap<>();
        if (until.isBefore(from)) {
            familyIds.forEach(it -> rates.put(it, null));
            return rates;
        }
        Map<UUID, List<ProfileSummary>> members = profiles.summariesOfFamilies(familyIds);
        Map<UUID, List<UUID>> children = new LinkedHashMap<>();
        familyIds.forEach(it -> children.put(it, countedChildren(members.getOrDefault(it, List.of()))));
        List<UUID> allChildren =
                children.values().stream().flatMap(List::stream).toList();
        Map<UUID, Set<LocalDate>> planned = plannedDays.plannedDaysSinceCreated(allChildren, from, until);
        // 잡힌 날이 없는 아이는 어차피 평균에서 빠진다 — 활동을 읽지 않는다
        List<UUID> plannedChildren = allChildren.stream()
                .filter(it -> !planned.getOrDefault(it, Set.of()).isEmpty())
                .toList();
        Map<UUID, List<DailyMinutes>> moved = activity.verifiedDaysOf(plannedChildren, from, until);
        Map<UUID, List<LocalDate>> rest = restDays.restDaysOfFamilies(familyIds, from, until);
        for (UUID familyId : familyIds) {
            List<ChildDays> days = new ArrayList<>();
            for (UUID child : children.get(familyId)) {
                Set<LocalDate> childPlanned = planned.getOrDefault(child, Set.of());
                if (childPlanned.isEmpty()) continue;
                days.add(new ChildDays(childPlanned, dates(moved.getOrDefault(child, List.of()))));
            }
            Set<LocalDate> familyRest = new HashSet<>(rest.getOrDefault(familyId, List.of()));
            rates.put(familyId, days.isEmpty() ? null : AchievementRate.measure(days, familyRest, from, until, today));
        }
        return rates;
    }

    /** 셀 아이 — 부모는 겨루지 않는다. 동의가 필요한데 없는 아이는 기록할 수 없어(422 CONSENT_REQUIRED) 셈에서 뺀다. */
    private static List<UUID> countedChildren(List<ProfileSummary> members) {
        return members.stream()
                .filter(it -> it.role() == ProfileRole.CHILD)
                .filter(it -> !it.consentRequired() || it.consentGiven())
                .map(ProfileSummary::profileId)
                .toList();
    }

    private static Set<LocalDate> dates(List<DailyMinutes> days) {
        return days.stream().map(DailyMinutes::date).collect(Collectors.toCollection(HashSet::new));
    }
}
