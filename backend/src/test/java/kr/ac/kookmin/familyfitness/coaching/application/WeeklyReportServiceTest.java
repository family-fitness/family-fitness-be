package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryCoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemorySessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyReportServiceTest {
    private final Family family = new Family();
    private final FakeIdentity identity = newIdentity();
    private final FakeActivity activity = new FakeActivity();
    private final InMemoryCoachRunRepository runs = new InMemoryCoachRunRepository();
    private final InMemorySessionCompletionRepository completions = new InMemorySessionCompletionRepository();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository(completions);
    private final MissionCompletionPolicy policy =
            new MissionCompletionPolicy(activity, new InMemoryVideoInteractionRepository(), missions, completions);
    private final MissionService missionService = new MissionService(
            missions, completions, new InMemoryExerciseVideoRepository(), identity, identity, policy, Fixed.time());
    private final WeeklyReportService service =
            new WeeklyReportService(runs, missions, identity, identity, activity, identity, policy, Fixed.time());

    private FakeIdentity newIdentity() {
        FakeIdentity fake = new FakeIdentity(family);
        fake.cheerCount = 4;
        return fake;
    }

    private static CoachRun awaiting(UUID familyId, String summary, Instant at) {
        return CoachRun.awaitingApproval(
                UUID.randomUUID(), familyId, List.of(), Fixed.WEEK_START, List.of(), summary, 3, 15, at);
    }

    @Test
    @DisplayName("이번 주 요약은 그 주 최신 실행의 summary·겹치는 미션 집계·구성원 활동·응원 수를 모은다")
    void 이번_주_요약은_그_주_최신_실행의_summary_겹치는_미션_집계_구성원_활동_응원_수를_모은다() {
        runs.save(awaiting(family.familyId, "옛 요약", Fixed.NOW.minusSeconds(60)));
        runs.save(awaiting(family.familyId, "이번 주 요약", Fixed.NOW));
        missionService.create(
                family.parentUser,
                family.familyId,
                new CreateMissionCommand(
                        "타이머",
                        Fixed.WEEK_START,
                        Fixed.WEEK_START.plusDays(6),
                        TargetMetric.TIMER_MINUTES,
                        20,
                        null,
                        List.of(family.child.profileId())));
        missionService.create(
                family.parentUser,
                family.familyId,
                new CreateMissionCommand(
                        "걸음",
                        Fixed.WEEK_START.plusDays(5),
                        Fixed.WEEK_START.plusDays(10),
                        TargetMetric.STEPS,
                        5000,
                        null,
                        List.of(family.child.profileId(), family.parent.profileId())));
        missionService.create(
                family.parentUser,
                family.familyId,
                new CreateMissionCommand(
                        "지난주",
                        Fixed.WEEK_START.minusDays(7),
                        Fixed.WEEK_START.minusDays(1),
                        TargetMetric.STEPS,
                        5000,
                        null,
                        List.of(family.child.profileId())));
        activity.addActiveMinutes(family.child.profileId(), Fixed.TODAY, ActivitySource.TIMER, 25);
        activity.overwriteSteps(family.child.profileId(), Fixed.TODAY, 3000);
        activity.addActiveMinutes(family.parent.profileId(), Fixed.WEEK_START.minusDays(1), ActivitySource.TIMER, 40);

        WeeklyReportView report = service.weekly(family.childUser, family.familyId, null);

        assertThat(report.weekStart()).isEqualTo(Fixed.WEEK_START);
        assertThat(report.weekEnd()).isEqualTo(Fixed.WEEK_START.plusDays(6));
        assertThat(report.summary()).isEqualTo("이번 주 요약");
        assertThat(report.missionStats().total()).isEqualTo(2);
        assertThat(report.missionStats().completed()).isEqualTo(1);
        assertThat(report.cheerCount()).isEqualTo(4);
        MemberReportView child = report.members().stream()
                .filter(it -> it.profileId().equals(family.child.profileId()))
                .findFirst()
                .orElseThrow();
        assertThat(child.name()).isEqualTo("민준");
        assertThat(child.activeMinutes()).isEqualTo(25);
        assertThat(child.verifiedMinutes()).isEqualTo(25);
        assertThat(child.completedMissions()).isEqualTo(1);
        MemberReportView parent = report.members().stream()
                .filter(it -> it.profileId().equals(family.parent.profileId()))
                .findFirst()
                .orElseThrow();
        assertThat(parent.activeMinutes()).isEqualTo(0);
        assertThat(parent.completedMissions()).isEqualTo(0);
    }

    @Test
    @DisplayName("weekStart 는 월요일로 맞추고 다른 가족은 볼 수 없다")
    void weekStart_는_월요일로_맞추고_다른_가족은_볼_수_없다() {
        WeeklyReportView report = service.weekly(family.parentUser, family.familyId, LocalDate.of(2026, 8, 30));

        assertThat(report.weekStart()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(report.summary()).isNull();
        assertThat(report.missionStats().total()).isEqualTo(0);
        assertThrows(NotSameFamilyException.class, () -> service.weekly(family.outsiderUser, family.familyId, null));
    }
}
