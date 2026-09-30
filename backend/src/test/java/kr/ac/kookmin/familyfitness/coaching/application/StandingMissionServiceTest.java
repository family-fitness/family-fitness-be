package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMission;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 오늘 서는 미션(알림 MISSION_READY 가 묻는다) — 잡힌 날과 같은 규칙, 끝낸 참여자는 뺀다. */
class StandingMissionServiceTest {
    private final LocalDate today = LocalDate.of(2026, 9, 29);
    private final Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
    private final UUID familyId = UUID.randomUUID();
    private final UUID kid = UUID.randomUUID();
    private final UUID sibling = UUID.randomUUID();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository();
    private final StandingMissionService service = new StandingMissionService(missions);

    private Mission mission(LocalDate from, LocalDate to, TargetMetric metric, MissionParticipant... participants) {
        Mission mission = Mission.reconstitute(
                UUID.randomUUID(),
                familyId,
                null,
                "스쿼트",
                null,
                MissionOrigin.MANUAL,
                metric,
                10,
                null,
                null,
                from,
                to,
                null,
                createdAt,
                List.of(participants),
                List.of());
        return missions.save(mission);
    }

    private MissionParticipant pending(UUID profileId) {
        return MissionParticipant.pending(profileId, null, createdAt);
    }

    private MissionParticipant done(UUID profileId) {
        return MissionParticipant.reconstitute(
                profileId, null, 1.0, ParticipantStatus.COMPLETED, VerifiedBy.TIMER, createdAt, null, createdAt);
    }

    @Test
    @DisplayName("하루짜리는 그날, 여러 날짜리는 기간 안이면 오늘 선다 — 다른 날 · 걸음수(STEPS)는 서지 않는다")
    void 오늘_서는_미션() {
        Mission single = mission(today, today, TargetMetric.TIMER_MINUTES, pending(kid));
        Mission multi = mission(today.minusDays(2), today.plusDays(2), TargetMetric.VIDEO_DONE, pending(kid));
        mission(today.plusDays(1), today.plusDays(1), TargetMetric.TIMER_MINUTES, pending(kid));
        mission(today.minusDays(1), today.minusDays(1), TargetMetric.TIMER_MINUTES, pending(kid));
        mission(today, today, TargetMetric.STEPS, pending(kid));

        assertThat(service.standingOn(familyId, today))
                .extracting(StandingMission::missionId)
                .containsExactlyInAnyOrder(single.getId(), multi.getId());
        assertThat(service.standingOn(UUID.randomUUID(), today)).isEmpty();
    }

    @Test
    @DisplayName("다 끝낸 참여자는 빼고, 남은 참여자가 없으면 그 미션은 나오지 않는다")
    void 끝낸_참여자는_뺀다() {
        Mission half = mission(today, today, TargetMetric.TIMER_MINUTES, done(kid), pending(sibling));
        Mission all = mission(today, today, TargetMetric.TIMER_MINUTES, done(kid), done(sibling));

        StandingMission standing = service.standing(half.getId(), today);
        assertThat(standing).isNotNull();
        assertThat(standing.pendingProfileIds()).containsExactly(sibling);
        assertThat(standing.title()).isEqualTo("스쿼트");
        assertThat(standing.familyId()).isEqualTo(familyId);
        assertThat(standing.createdAt()).isEqualTo(createdAt);
        assertThat(service.standing(all.getId(), today)).isNull();
        assertThat(service.standingOn(familyId, today))
                .extracting(StandingMission::missionId)
                .containsExactly(half.getId());
    }

    @Test
    @DisplayName("그날 기간이 걸친 미션이 있는 가족만 한 번에 — 다른 날에만 미션이 있는 가족은 빠지고, 한 가족은 한 번만")
    void 그날_미션이_있는_가족() {
        mission(today, today, TargetMetric.TIMER_MINUTES, pending(kid));
        mission(today.minusDays(2), today.plusDays(2), TargetMetric.VIDEO_DONE, pending(sibling));
        UUID otherFamily = UUID.randomUUID();
        missions.save(Mission.reconstitute(
                UUID.randomUUID(),
                otherFamily,
                null,
                "줄넘기",
                null,
                MissionOrigin.MANUAL,
                TargetMetric.TIMER_MINUTES,
                10,
                null,
                null,
                today.plusDays(1),
                today.plusDays(1),
                null,
                createdAt,
                List.of(pending(UUID.randomUUID())),
                List.of()));

        assertThat(service.familiesWithMissionsOn(today)).containsExactly(familyId);
        assertThat(service.familiesWithMissionsOn(today.plusDays(1))).containsExactlyInAnyOrder(familyId, otherFamily);
        assertThat(service.familiesWithMissionsOn(today.plusDays(3))).isEmpty();
    }

    @Test
    @DisplayName("미션 하나 묻기 — 없는 미션 · 기간 밖 · 걸음수는 null")
    void 미션_하나() {
        Mission steps = mission(today, today, TargetMetric.STEPS, pending(kid));
        Mission tomorrow = mission(today.plusDays(1), today.plusDays(1), TargetMetric.TIMER_MINUTES, pending(kid));

        assertThat(service.standing(UUID.randomUUID(), today)).isNull();
        assertThat(service.standing(steps.getId(), today)).isNull();
        assertThat(service.standing(tomorrow.getId(), today)).isNull();
        assertThat(service.standing(tomorrow.getId(), today.plusDays(1))).isNotNull();
    }
}
