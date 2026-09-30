package kr.ac.kookmin.familyfitness.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMission;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMissionQuery;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationKind;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 알림 만들기 — 응원 · 업적 · 오늘 서는 미션 · 다시 재기 · 미션 끝 · 미션 지움. 저장소는 메모리, 다른 모듈은 가짜다. */
class NotificationWriterTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LocalDate today = LocalDate.of(2026, 9, 29);
    private final Instant readyAt = Instant.parse("2026-09-28T22:30:00Z"); // 9/29 07:30 KST
    private final UUID familyId = UUID.randomUUID();
    private final UUID mom = UUID.randomUUID();
    private final UUID dad = UUID.randomUUID();
    private final UUID kid = UUID.randomUUID();
    private final UUID sibling = UUID.randomUUID();

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final ProfileQuery profiles = mock(ProfileQuery.class);
    private final FitnessQuery fitness = mock(FitnessQuery.class);
    private final Set<LocalDate> restDays = new HashSet<>();
    private final List<StandingMission> standing = new ArrayList<>();
    private final Map<UUID, LocalDate> lastTested = new HashMap<>();

    private final StandingMissionQuery standingQuery = new StandingMissionQuery() {
        @Override
        public List<StandingMission> standingOn(UUID family, LocalDate day) {
            return family.equals(familyId) && day.equals(today) ? standing : List.of();
        }

        @Override
        public @Nullable StandingMission standing(UUID missionId, LocalDate day) {
            return standingOn(familyId, day).stream()
                    .filter(it -> it.missionId().equals(missionId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<UUID> familiesWithMissionsOn(LocalDate day) {
            return standingOn(familyId, day).isEmpty() ? List.of() : List.of(familyId);
        }
    };

    private final NotificationWriter writer = new NotificationWriter(
            repository,
            profiles,
            (family, from, to) -> restDays.stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .sorted()
                    .toList(),
            standingQuery,
            fitness,
            KST);

    @BeforeEach
    void setUp() {
        List<ProfileSummary> family = List.of(
                summary(mom, "은영", ProfileRole.PARENT, Sex.F),
                summary(dad, "철수", ProfileRole.PARENT, Sex.M),
                summary(kid, "서준", ProfileRole.CHILD, Sex.M),
                summary(sibling, "지우", ProfileRole.CHILD, Sex.F));
        when(profiles.summariesOfFamily(familyId)).thenReturn(family);
        family.forEach(it -> when(profiles.findSummary(it.profileId())).thenReturn(it));
        when(fitness.lastTestedOn(any())).thenAnswer(call -> lastTested);
    }

    private ProfileSummary summary(UUID profileId, String name, ProfileRole role, Sex sex) {
        return new ProfileSummary(
                profileId,
                familyId,
                name,
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                sex,
                role == ProfileRole.PARENT,
                InviteStatus.NONE,
                null,
                true,
                role == ProfileRole.CHILD,
                true);
    }

    private CheerSent cheer(
            CheerKind kind, UUID from, UUID to, @Nullable String stickerId, @Nullable String message, UUID mission) {
        // 9/29 00:30 KST — UTC 로는 9/28 이라, 날짜를 KST 로 셈하는지 가른다
        return new CheerSent(
                UUID.randomUUID(),
                familyId,
                from,
                to,
                kind,
                stickerId,
                message,
                mission,
                null,
                Instant.parse("2026-09-28T15:30:00Z"));
    }

    private StandingMission mission(String title, Instant createdAt, UUID... pending) {
        StandingMission mission = new StandingMission(UUID.randomUUID(), familyId, title, createdAt, List.of(pending));
        standing.add(mission);
        return mission;
    }

    @Nested
    @DisplayName("응원")
    class Cheers {
        @Test
        @DisplayName("DONE → 그 응원을 받은 부모에게 KID_DONE 한 건 — 날짜는 KST, 같은 응원을 다시 받아도 한 건")
        void 다_했어요() {
            UUID mission = UUID.randomUUID();
            CheerSent done = cheer(CheerKind.DONE, kid, mom, null, "운동 3개 했어요!", mission);
            assertThat(writer.fromCheer(done)).isEqualTo(1);
            assertThat(writer.fromCheer(done)).isZero();

            assertThat(repository.of(dad)).isEmpty();
            Notification n = repository.of(mom).getFirst();
            assertThat(n.kind()).isEqualTo(NotificationKind.KID_DONE);
            assertThat(n.title()).isEqualTo("서준이 운동을 마쳤어요");
            assertThat(n.body()).isEqualTo("운동 3개 했어요!");
            assertThat(n.aboutProfileId()).isEqualTo(kid);
            assertThat(n.missionId()).isEqualTo(mission);
            assertThat(n.cheerId()).isEqualTo(done.cheerId());
            assertThat(n.date()).isEqualTo(today);
            assertThat(n.createdAt()).isEqualTo(done.createdAt());
        }

        @Test
        @DisplayName("THANKS → 받은 부모에게 KID_THANKS, 본문은 스티커 이름 · 보낸 이는 아이")
        void 고마워요() {
            writer.fromCheer(cheer(CheerKind.THANKS, sibling, dad, "kiumi", null, UUID.randomUUID()));

            assertThat(repository.of(dad))
                    .extracting(
                            Notification::kind, Notification::title, Notification::body, Notification::fromProfileId)
                    .containsExactly(tuple(NotificationKind.KID_THANKS, "지우가 고맙대요", "꼭 안아 줄게", sibling));
        }

        @Test
        @DisplayName("PRAISE → 받은 아이에게, 보낸 이는 이름이 아니라 엄마 · 아빠 — 스티커가 없으면 「칭찬을 보냈어요」")
        void 칭찬() {
            writer.fromCheer(cheer(CheerKind.PRAISE, mom, kid, "star", null, UUID.randomUUID()));
            writer.fromCheer(cheer(CheerKind.PRAISE, dad, kid, null, "멋지다", UUID.randomUUID()));

            assertThat(repository.of(kid))
                    .extracting(
                            Notification::kind, Notification::title, Notification::body, Notification::fromProfileId)
                    .containsExactly(
                            tuple(NotificationKind.PRAISE, "은영이 스티커를 붙여 줬어요", null, mom),
                            tuple(NotificationKind.PRAISE, "철수가 칭찬을 보냈어요", "멋지다", dad));
            assertThat(repository.of(mom)).isEmpty();
        }

        @Test
        @DisplayName("보낸 프로필을 못 찾으면 만들지 않는다")
        void 보낸_사람을_모르면() {
            assertThat(writer.fromCheer(cheer(CheerKind.DONE, UUID.randomUUID(), mom, null, "했어요", UUID.randomUUID())))
                    .isZero();
            assertThat(repository.rows).isEmpty();
        }
    }

    @Test
    @DisplayName("업적 — 아이 프로필이면 한 건(본문은 지난 말), 부모 프로필이 받은 업적은 알리지 않는다")
    void 업적() {
        Instant earnedAt = Instant.parse("2026-09-28T16:00:00Z");
        AchievementEarned kidBadge = new AchievementEarned(kid, "STREAK_3", "사흘 이어서", "3일 이어서 움직여요", earnedAt);
        assertThat(writer.achievement(kidBadge)).isEqualTo(1);
        assertThat(writer.achievement(kidBadge)).isZero();
        assertThat(writer.achievement(new AchievementEarned(mom, "FIRST_STEP", "첫걸음", "운동 한 칸을 처음 끝내요", earnedAt)))
                .isZero();

        assertThat(repository.rows)
                .extracting(Notification::profileId, Notification::title, Notification::body, Notification::date)
                .containsExactly(tuple(kid, "새 업적: 사흘 이어서", "3일 이어서 움직였어요", today));
    }

    @Nested
    @DisplayName("오늘 서는 미션(MISSION_READY)")
    class MissionReady {
        @Test
        @DisplayName("안 끝낸 참여 아이마다 한 건 — 부모 참여자는 빼고, 만든 시각은 07:30(그 뒤에 생긴 미션이면 생긴 시각)")
        void 아이마다_한_건() {
            StandingMission early = mission("스쿼트", readyAt.minusSeconds(3600), kid, sibling, mom);
            Instant lateAt = readyAt.plusSeconds(600);
            StandingMission late = mission("줄넘기", lateAt, kid);

            assertThat(writer.missionReadyForFamily(familyId, today, readyAt)).isEqualTo(3);
            assertThat(writer.missionReadyForFamily(familyId, today, readyAt)).isZero();

            assertThat(repository.of(mom)).isEmpty();
            assertThat(repository.of(kid))
                    .extracting(
                            Notification::missionId, Notification::body, Notification::createdAt, Notification::date)
                    .containsExactlyInAnyOrder(
                            tuple(early.missionId(), "스쿼트", readyAt, today),
                            tuple(late.missionId(), "줄넘기", lateAt, today));
            assertThat(repository.of(sibling)).extracting(Notification::title).containsExactly("새 운동이 생겼어요");
        }

        @Test
        @DisplayName("쉬는 날에는 만들지 않는다 — 한 일에 대한 알림(응원)은 그대로")
        void 쉬는_날() {
            mission("스쿼트", readyAt, kid);
            restDays.add(today);

            assertThat(writer.missionReadyForFamily(familyId, today, readyAt)).isZero();
            assertThat(writer.missionReadyForMission(standing.getFirst().missionId(), today, readyAt))
                    .isZero();
            assertThat(writer.fromCheer(cheer(CheerKind.DONE, kid, mom, null, "했어요", UUID.randomUUID())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("07:30 뒤에 생긴 미션은 그 미션만 곧바로 — 오늘 서지 않으면(없음 · 걸음수 · 다른 날) 만들지 않는다")
        void 새_미션() {
            StandingMission mission = mission("스쿼트", readyAt, kid, mom);
            Instant createdAt = readyAt.plusSeconds(7200);

            assertThat(writer.missionReadyForMission(mission.missionId(), today, createdAt))
                    .isEqualTo(1);
            assertThat(writer.missionReadyForMission(UUID.randomUUID(), today, createdAt))
                    .isZero();
            assertThat(repository.rows)
                    .extracting(Notification::profileId, Notification::createdAt)
                    .containsExactly(tuple(kid, createdAt));
        }

        @Test
        @DisplayName("미션을 끝낸 사람의 그 미션 MISSION_READY 만 빠진다 — 형제 것 · 같은 미션의 다른 알림은 남는다")
        void 끝내면_빠진다() {
            StandingMission mission = mission("스쿼트", readyAt, kid, sibling);
            writer.missionReadyForFamily(familyId, today, readyAt);
            writer.fromCheer(cheer(CheerKind.PRAISE, mom, kid, "star", null, mission.missionId()));

            assertThat(writer.missionCompleted(kid, mission.missionId())).isEqualTo(1);

            assertThat(repository.of(kid)).extracting(Notification::kind).containsExactly(NotificationKind.PRAISE);
            assertThat(repository.of(sibling))
                    .extracting(Notification::kind)
                    .containsExactly(NotificationKind.MISSION_READY);
        }

        @Test
        @DisplayName("미션이 지워지면 그 미션에 걸린 알림을 모두 지운다 — 다른 미션 알림은 남는다")
        void 지우면_모두_지운다() {
            StandingMission gone = mission("스쿼트", readyAt, kid);
            StandingMission kept = mission("줄넘기", readyAt, kid);
            writer.missionReadyForFamily(familyId, today, readyAt);
            writer.fromCheer(cheer(CheerKind.DONE, kid, mom, null, "했어요", gone.missionId()));

            assertThat(writer.missionCancelled(gone.missionId())).isEqualTo(2);
            assertThat(repository.rows).extracting(Notification::missionId).containsExactly(kept.missionId());
        }
    }

    @Nested
    @DisplayName("다시 재기(REMEASURE)")
    class Remeasure {
        private final Instant at = Instant.parse("2026-09-29T00:00:00Z"); // 09:00 KST

        @Test
        @DisplayName("마지막 측정에서 30일 이상 지난 아이 → 부모 전원에게 한 건씩. 29일 · 안 잰 아이는 건너뛴다")
        void 부모_전원() {
            lastTested.put(kid, today.minusDays(30));
            lastTested.put(sibling, today.minusDays(29));

            assertThat(writer.remeasureForFamily(familyId, today, at)).isEqualTo(2);

            assertThat(repository.rows)
                    .extracting(
                            Notification::profileId,
                            Notification::title,
                            Notification::body,
                            Notification::aboutProfileId)
                    .containsExactlyInAnyOrder(
                            tuple(mom, "서준 키와 몸무게를 새로 재 볼까요", "지난번에 잰 지 30일", kid),
                            tuple(dad, "서준 키와 몸무게를 새로 재 볼까요", "지난번에 잰 지 30일", kid));
        }

        @Test
        @DisplayName("측정 회차마다 한 번 — 다음 날 다시 돌아도 늘지 않고, 새로 재면 그 회차로 30일 뒤 또 간다")
        void 회차마다_한_번() {
            lastTested.put(kid, today.minusDays(30));
            writer.remeasureForFamily(familyId, today, at);
            assertThat(writer.remeasureForFamily(familyId, today.plusDays(1), at.plusSeconds(86_400)))
                    .isZero();

            lastTested.put(kid, today.plusDays(1));
            assertThat(writer.remeasureForFamily(familyId, today.plusDays(31), at))
                    .isEqualTo(2);
            assertThat(repository.of(mom))
                    .extracting(Notification::dedupeKey)
                    .containsExactlyInAnyOrder(
                            "remeasure-" + kid + "-" + today.minusDays(30),
                            "remeasure-" + kid + "-" + today.plusDays(1));
        }

        @Test
        @DisplayName("다시 재면 그 아이의 지난 회차 알림이 부모 모두에게서 빠진다 — 형제 것은 남는다")
        void 다시_재면_빠진다() {
            lastTested.put(kid, today.minusDays(40));
            lastTested.put(sibling, today.minusDays(35));
            writer.remeasureForFamily(familyId, today, at);
            assertThat(repository.rows).hasSize(4);

            lastTested.put(kid, today);
            assertThat(writer.remeasured(kid)).isEqualTo(2);

            assertThat(repository.rows)
                    .extracting(Notification::profileId, Notification::aboutProfileId)
                    .containsExactlyInAnyOrder(tuple(mom, sibling), tuple(dad, sibling));
        }

        @Test
        @DisplayName("지난 날짜를 나중에 적어 마지막 측정일이 그대로면 그 회차의 알림은 남는다(목도 그대로 보인다)")
        void 마지막_측정일이_그대로면_남는다() {
            lastTested.put(kid, today.minusDays(40));
            writer.remeasureForFamily(familyId, today, at);

            assertThat(writer.remeasured(kid)).isZero();
            assertThat(repository.of(mom)).hasSize(1);
        }

        @Test
        @DisplayName("부모 · 모르는 프로필의 측정이면 지우기 쿼리를 돌리지 않는다 — REMEASURE 는 아이에 관해서만 생긴다")
        void 아이가_아니면_지우지_않는다() {
            lastTested.put(kid, today.minusDays(40));
            writer.remeasureForFamily(familyId, today, at);
            lastTested.put(mom, today);

            assertThat(writer.remeasured(mom)).isZero();
            assertThat(writer.remeasured(UUID.randomUUID())).isZero();

            assertThat(repository.remeasureDeletes).isZero();
            assertThat(repository.rows).hasSize(2);
        }
    }
}
