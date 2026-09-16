package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주간 요약(월~일). 그 주에 겹치는 미션과 구성원 활동, 응원 수를 모은다. */
@Service
public class WeeklyReportService {
    private final CoachRunRepository runs;
    private final MissionRepository missions;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final ActivityQuery activityQuery;
    private final CheerQuery cheerQuery;
    private final MissionCompletionPolicy policy;
    private final AppTime time;

    public WeeklyReportService(
            CoachRunRepository runs,
            MissionRepository missions,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            ActivityQuery activityQuery,
            CheerQuery cheerQuery,
            MissionCompletionPolicy policy,
            AppTime time) {
        this.runs = runs;
        this.missions = missions;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.activityQuery = activityQuery;
        this.cheerQuery = cheerQuery;
        this.policy = policy;
        this.time = time;
    }

    @Transactional
    public WeeklyReportView weekly(UUID userId, UUID familyId, @Nullable LocalDate weekStartParam) {
        familyAccess.requireMember(userId, familyId);
        LocalDate weekStart = weekStartParam == null ? time.thisWeekStart() : AppTime.weekStartOf(weekStartParam);
        LocalDate weekEnd = weekStart.plusDays(6);
        Instant now = time.now();

        List<Mission> weekMissions = missions.findOverlapping(familyId, weekStart, weekEnd).stream()
                .map(it -> policy.refreshAll(it, now))
                .toList();
        List<MemberReportView> members = profileQuery.summariesOfFamily(familyId).stream()
                .map(member -> {
                    ActivityTotals totals = activityQuery.totals(member.profileId(), weekStart, weekEnd);
                    int completedMissions = (int) weekMissions.stream()
                            .filter(m -> m.getParticipants().stream()
                                    .anyMatch(it -> it.getProfileId().equals(member.profileId()) && it.isCompleted()))
                            .count();
                    return new MemberReportView(
                            member.profileId(),
                            member.name(),
                            totals.activeMinutes(),
                            totals.verifiedMinutes(),
                            completedMissions);
                })
                .toList();
        CoachRun latest = runs.findLatestOfWeek(familyId, weekStart);
        int completed =
                (int) weekMissions.stream().filter(Mission::isAllCompleted).count();
        return new WeeklyReportView(
                weekStart,
                weekEnd,
                latest == null ? null : latest.getSummary(),
                new MissionStatsView(weekMissions.size(), completed),
                members,
                cheerQuery.countCheers(familyId, time.startOfDay(weekStart), time.startOfDay(weekEnd.plusDays(1))));
    }
}
