package kr.ac.kookmin.familyfitness.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered.Round;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone.Phase;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone.Verification;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;
import kr.ac.kookmin.familyfitness.progress.domain.XpKind;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 칸 끝 적립 · 스티커 · 다시 재기 · 업적 · 읽기. 저장소는 메모리 가짜, 활동은 coaching 시험의 가짜를 쓴다. */
class ProgressServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 2026-09-24 목요일 */
    private final LocalDate today = LocalDate.of(2026, 9, 24);

    private final SteppingClock clock = new SteppingClock(Instant.parse("2026-09-24T10:00:00Z"));
    private final UUID familyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID mom = UUID.randomUUID();
    private final UUID dad = UUID.randomUUID();
    private final UUID kid = UUID.randomUUID();
    private final UUID sibling = UUID.randomUUID();
    /** 식구 이름 · 성별 — 스티커 줄이 붙인 사람을 부를 때 쓴다 */
    private final Map<UUID, String> names = Map.of(mom, "은영", dad, "철수", kid, "서준", sibling, "하린");

    private final Map<UUID, Sex> sexes = Map.of(mom, Sex.F, dad, Sex.M, kid, Sex.M, sibling, Sex.F);

    private final InMemoryXpLedger ledger = new InMemoryXpLedger();
    private final InMemoryAchievementStore achievements = new InMemoryAchievementStore();
    private final FakeActivity activity = new FakeActivity();
    private final Set<LocalDate> restDays = new HashSet<>();
    private final Map<UUID, Set<LocalDate>> planned = new HashMap<>();
    private final ProfileQuery profiles = mock(ProfileQuery.class);
    private final FamilyAccess familyAccess = mock(FamilyAccess.class);

    private final MoveHistory history = new MoveHistory(
            activity,
            (family, from, to) -> restDays.stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .sorted()
                    .toList(),
            (profileId, from, to) -> planned.getOrDefault(profileId, Set.of()));
    /** 처음 받은 업적마다 발행한 AchievementEarned. */
    private final List<AchievementEarned> earnedEvents = new ArrayList<>();

    private final AchievementAwards awards = new AchievementAwards(achievements, event -> {
        if (event instanceof AchievementEarned earned) earnedEvents.add(earned);
    });
    private final ProgressRecorderService recorder =
            new ProgressRecorderService(ledger, awards, history, activity, profiles, clock);
    private final ProgressEventListener listener = new ProgressEventListener(ledger, awards, clock, KST);
    private final ProgressQueryService query =
            new ProgressQueryService(ledger, achievements, history, activity, familyAccess, profiles, clock, KST);

    @BeforeEach
    void setUp() {
        when(profiles.summariesOfFamily(familyId))
                .thenReturn(List.of(
                        summary(mom, ProfileRole.PARENT),
                        summary(dad, ProfileRole.PARENT),
                        summary(kid, ProfileRole.CHILD),
                        summary(sibling, ProfileRole.CHILD)));
        for (UUID id : List.of(mom, dad, kid, sibling)) {
            when(familyAccess.requireSameFamilyAsProfile(userId, id))
                    .thenReturn(summary(id, id == mom || id == dad ? ProfileRole.PARENT : ProfileRole.CHILD));
        }
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role) {
        return new ProfileSummary(
                profileId,
                familyId,
                names.getOrDefault(profileId, "이름"),
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                sexes.getOrDefault(profileId, Sex.F),
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                role == ProfileRole.CHILD,
                true,
                false);
    }

    private SessionDone done(UUID profileId, UUID missionId, int position, Phase phase, LocalDate on) {
        return new SessionDone(familyId, profileId, missionId, position, phase, null, false, null, on, clock.instant());
    }

    private SessionDone finished(UUID profileId, UUID missionId, int position, Verification by, LocalDate on) {
        return new SessionDone(
                familyId, profileId, missionId, position, Phase.COOLDOWN, null, true, by, on, clock.instant());
    }

    /** 그날 타이머로 잰 분을 쌓고 칸 끝을 알린다 — 칸 끝은 활동을 먼저 기록하고 부른다. */
    private int move(UUID profileId, UUID missionId, int position, Phase phase, LocalDate on, int minutes) {
        activity.addActiveMinutes(profileId, on, ActivitySource.TIMER, minutes);
        clock.step();
        return recorder.sessionDone(done(profileId, missionId, position, phase, on));
    }

    private CheerSent cheer(CheerKind kind, UUID from, UUID to, String stickerId, UUID missionId) {
        clock.step();
        return new CheerSent(
                UUID.randomUUID(), familyId, from, to, kind, stickerId, null, missionId, null, clock.instant());
    }

    /** 새 회차 자신이 다시 잰 회차인 등록(그보다 이른 회차가 이미 있다). */
    private FitnessTestRegistered remeasuredOn(LocalDate testedOn) {
        UUID testId = UUID.randomUUID();
        return new FitnessTestRegistered(kid, testId, testedOn, new Round(testId, testedOn));
    }

    private Instant earnedAt(UUID profileId, Achievement achievement) {
        return achievements.earnedOf(profileId).get(achievement);
    }

    @Nested
    @DisplayName("칸 끝 적립")
    class SessionDoneXp {
        @Test
        @DisplayName("칸을 처음 끝내면 +5, 같은 칸을 다시 보내면 0")
        void 칸을_처음_끝내면_5() {
            UUID mission = UUID.randomUUID();
            assertThat(recorder.sessionDone(done(kid, mission, 1, Phase.WARMUP, today)))
                    .isEqualTo(5);
            assertThat(recorder.sessionDone(done(kid, mission, 1, Phase.WARMUP, today)))
                    .isZero();
            assertThat(ledger.totalOf(kid)).isEqualTo(5);
        }

        @Test
        @DisplayName("마지막 칸으로 미션이 끝나면 +5 +20, 다시 보내면 0 — 운동마다 붙는다(결정 23)")
        void 미션이_끝나면_20() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            assertThat(recorder.sessionDone(finished(kid, first, 1, Verification.TIMER, today)))
                    .isEqualTo(25);
            assertThat(recorder.sessionDone(finished(kid, first, 1, Verification.TIMER, today)))
                    .isZero();
            assertThat(recorder.sessionDone(finished(kid, second, 1, Verification.VIDEO_PROGRESS, today)))
                    .isEqualTo(25);
            assertThat(ledger.totalOf(kid)).isEqualTo(50);
        }

        @Test
        @DisplayName("자기 신고(SELF_REPORT)로 끝난 미션에는 +20 을 주지 않는다")
        void 자기_신고는_20_없음() {
            assertThat(recorder.sessionDone(finished(kid, UUID.randomUUID(), 1, Verification.SELF_REPORT, today)))
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("원장은 사람마다다 — 형제가 같은 칸을 끝내도 각자 +5")
        void 원장은_사람마다다() {
            UUID shared = UUID.randomUUID();
            assertThat(recorder.sessionDone(done(kid, shared, 1, Phase.MAIN, today)))
                    .isEqualTo(5);
            assertThat(recorder.sessionDone(done(sibling, shared, 1, Phase.MAIN, today)))
                    .isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("칭찬 스티커 · 다시 재기")
    class EventXp {
        @Test
        @DisplayName("부모의 칭찬 스티커는 +10, 같은 운동에는 한 번 — 첫 스티커 업적은 첫 장의 시각")
        void 칭찬_스티커는_같은_운동에_한_번() {
            UUID mission = UUID.randomUUID();
            CheerSent first = cheer(CheerKind.PRAISE, mom, kid, "star", mission);
            listener.on(first);
            listener.on(cheer(CheerKind.PRAISE, dad, kid, "crown", mission));
            assertThat(ledger.totalOf(kid)).isEqualTo(10);
            assertThat(earnedAt(kid, Achievement.FIRST_STICKER)).isEqualTo(first.createdAt());
        }

        @Test
        @DisplayName("운동에 붙지 않은 칭찬 스티커는 한 장마다 +10")
        void 운동_없는_스티커는_한_장마다() {
            listener.on(cheer(CheerKind.PRAISE, mom, kid, "star", null));
            listener.on(cheer(CheerKind.PRAISE, mom, kid, "star", null));
            assertThat(ledger.totalOf(kid)).isEqualTo(20);
        }

        @Test
        @DisplayName("고마워요 · 알리기 · 스티커 없는 칭찬은 경험치도 첫 스티커 업적도 없다(결정 24)")
        void 고마워요와_알리기는_없다() {
            listener.on(cheer(CheerKind.THANKS, kid, mom, "heart", null));
            listener.on(cheer(CheerKind.DONE, kid, mom, null, null));
            listener.on(cheer(CheerKind.PRAISE, mom, kid, null, null));
            assertThat(ledger.rows).isEmpty();
            assertThat(achievements.earnedOf(mom)).isEmpty();
            assertThat(achievements.earnedOf(kid)).isEmpty();
        }

        @Test
        @DisplayName("다시 잰 회차마다 +20 — 원장 키 · 날짜는 이벤트가 알려 준 그 회차 것, 처음 다시 잰 회차에 「다시 측정」")
        void 다시_재기() {
            UUID first = UUID.randomUUID();
            LocalDate firstOn = today.minusDays(60);
            listener.on(new FitnessTestRegistered(kid, first, firstOn, null));
            assertThat(ledger.totalOf(kid)).isZero();
            assertThat(achievements.earnedOf(kid)).isEmpty();

            FitnessTestRegistered second = remeasuredOn(today.minusDays(30));
            listener.on(second);
            Instant remeasured = clock.instant();
            clock.step();
            // 지난 날짜를 나중에 적으면 fitness 가 그때까지 가장 이르던 회차를 알려 준다 — 새 회차가 아니라 그 회차로 쌓는다
            listener.on(
                    new FitnessTestRegistered(kid, UUID.randomUUID(), today.minusDays(90), new Round(first, firstOn)));
            // 같은 회차가 다시 와도 두 번 쌓지 않는다
            listener.on(second);

            assertThat(ledger.totalOf(kid)).isEqualTo(40);
            assertThat(ledger.rows)
                    .extracting(XpEvent::sourceKey, XpEvent::occurredOn)
                    .containsExactly(
                            tuple(second.fitnessTestId().toString(), today.minusDays(30)),
                            tuple(first.toString(), firstOn));
            assertThat(earnedAt(kid, Achievement.REMEASURE)).isEqualTo(remeasured);
        }
    }

    @Nested
    @DisplayName("업적 판정")
    class Achievements {
        @Test
        @DisplayName("칸을 처음 끝내면 첫걸음 — earnedAt 은 칸을 끝낸 시각, 다시 끝내도 바뀌지 않는다")
        void 첫걸음() {
            UUID mission = UUID.randomUUID();
            move(kid, mission, 1, Phase.MAIN, today, 3);
            Instant first = earnedAt(kid, Achievement.FIRST_STEP);
            assertThat(first).isNotNull();
            move(kid, mission, 2, Phase.MAIN, today, 3);
            assertThat(earnedAt(kid, Achievement.FIRST_STEP)).isEqualTo(first);
            assertThat(achievements.earnedOf(kid))
                    .doesNotContainKeys(Achievement.FULL_SET, Achievement.MIN_30, Achievement.WEEKEND);
        }

        @Test
        @DisplayName("처음 받은 업적만 AchievementEarned 로 알린다 — 같은 업적을 다시 판정하면 내지 않는다(알림이 두 번 가지 않게)")
        void 처음_받은_업적만_알린다() {
            UUID mission = UUID.randomUUID();
            move(kid, mission, 1, Phase.MAIN, today, 3);
            move(kid, mission, 2, Phase.MAIN, today, 3);

            assertThat(earnedEvents)
                    .containsExactly(new AchievementEarned(
                            kid, "FIRST_STEP", "첫걸음", "운동 1개를 처음 완료해요", earnedAt(kid, Achievement.FIRST_STEP)));
        }

        @Test
        @DisplayName("응원 · 측정으로 받은 업적도 알린다 — 첫 스티커는 한 번, 부모가 받은 업적도 낸다(누구에게 알릴지는 듣는 쪽이 정한다)")
        void 응원과_측정의_업적도_알린다() {
            listener.on(cheer(CheerKind.PRAISE, mom, kid, "star", null));
            listener.on(cheer(CheerKind.PRAISE, dad, kid, "crown", null));
            move(mom, UUID.randomUUID(), 1, Phase.MAIN, today, 3);

            assertThat(earnedEvents)
                    .extracting(AchievementEarned::profileId, AchievementEarned::code)
                    .containsExactly(tuple(kid, "FIRST_STICKER"), tuple(mom, "FIRST_STEP"));
        }

        @Test
        @DisplayName("준비운동부터 정리운동까지 — 그날 끝낸 칸에 준비 · 본 · 정리가 다 있으면. 여러 운동에 걸쳐도 된다")
        void 준비운동부터_정리운동까지() {
            move(kid, UUID.randomUUID(), 1, Phase.WARMUP, today.minusDays(1), 1);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 2);
            move(kid, UUID.randomUUID(), 1, Phase.COOLDOWN, today, 1);
            assertThat(achievements.earnedOf(kid)).doesNotContainKey(Achievement.FULL_SET);

            move(kid, UUID.randomUUID(), 1, Phase.WARMUP, today, 1);
            assertThat(achievements.earnedOf(kid)).containsKey(Achievement.FULL_SET);
        }

        @Test
        @DisplayName("모두 합쳐 30 · 100분 — 서버가 잰 분의 합으로, 걸음수(MANUAL)는 빼고")
        void 누적_분() {
            activity.overwriteSteps(kid, today, 9000);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today.minusDays(3), 25);
            assertThat(achievements.earnedOf(kid)).doesNotContainKey(Achievement.MIN_30);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 5);
            assertThat(achievements.earnedOf(kid))
                    .containsKey(Achievement.MIN_30)
                    .doesNotContainKey(Achievement.MIN_100);
        }

        @Test
        @DisplayName("주말에도 — 토 · 일에 움직인 날이 있으면")
        void 주말에도() {
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 3);
            assertThat(achievements.earnedOf(kid)).doesNotContainKey(Achievement.WEEKEND);
            LocalDate sunday = LocalDate.of(2026, 9, 27);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, sunday, 3);
            assertThat(achievements.earnedOf(kid)).containsKey(Achievement.WEEKEND);
        }

        @Test
        @DisplayName("3일 연속 — 잡힌 날 기준 셈. 잡히지 않은 날은 건너뛰고, 잡힌 날을 빼먹으면 끊긴다")
        void 연속_3일() {
            // 월(21) · 수(23) · 목(24) 에 움직였고 화(22)는 잡히지 않았다
            planned.put(kid, Set.of(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23), today));
            activity.addActiveMinutes(kid, LocalDate.of(2026, 9, 21), ActivitySource.TIMER, 5);
            activity.addActiveMinutes(kid, LocalDate.of(2026, 9, 23), ActivitySource.TIMER, 5);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 5);
            assertThat(achievements.earnedOf(kid))
                    .containsKey(Achievement.STREAK_3)
                    .doesNotContainKey(Achievement.STREAK_7);

            // 형제는 잡힌 수(23)를 빼먹어 목요일에 1
            planned.put(sibling, Set.of(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23), today));
            activity.addActiveMinutes(sibling, LocalDate.of(2026, 9, 21), ActivitySource.TIMER, 5);
            activity.addActiveMinutes(sibling, LocalDate.of(2026, 9, 22), ActivitySource.TIMER, 5);
            move(sibling, UUID.randomUUID(), 1, Phase.MAIN, today, 5);
            assertThat(achievements.earnedOf(sibling)).doesNotContainKey(Achievement.STREAK_3);
        }

        @Test
        @DisplayName("가족과 함께 — 아이가 먼저 하고 보호자가 나중에 해도 아이가 받는다. 보호자는 다른 보호자와 같은 날이어야 받는다")
        void 가족과_함께() {
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 5);
            assertThat(achievements.earnedOf(kid)).doesNotContainKey(Achievement.TOGETHER);

            move(mom, UUID.randomUUID(), 1, Phase.MAIN, today, 5);
            assertThat(achievements.earnedOf(kid)).containsKey(Achievement.TOGETHER);
            assertThat(achievements.earnedOf(sibling)).doesNotContainKey(Achievement.TOGETHER);
            assertThat(achievements.earnedOf(mom)).doesNotContainKey(Achievement.TOGETHER);

            move(dad, UUID.randomUUID(), 1, Phase.MAIN, today, 5);
            assertThat(achievements.earnedOf(dad)).containsKey(Achievement.TOGETHER);
            assertThat(achievements.earnedOf(mom)).containsKey(Achievement.TOGETHER);
        }
    }

    @Nested
    @DisplayName("읽기")
    class View {
        @Test
        @DisplayName("아무것도 없으면 Lv.1 · 0 · 다음 80 · 연속 0 · 운동한 날 0 · 업적 열두 개 모두 아직 · 최근 줄 없음")
        void 빈_사람() {
            ProgressView view = query.view(userId, kid);
            assertThat(view.level()).isEqualTo(1);
            assertThat(view.xp()).isZero();
            assertThat(view.levelFloorXp()).isZero();
            assertThat(view.nextLevelXp()).isEqualTo(80);
            assertThat(view.streakDays()).isZero();
            assertThat(view.activeDays()).isZero();
            assertThat(view.achievements()).hasSize(12).allMatch(it -> it.earnedAt() == null);
            assertThat(view.achievements().getFirst().title()).isEqualTo("첫걸음");
            assertThat(view.recentXp()).isEmpty();
        }

        @Test
        @DisplayName("레벨 · 구간 · 운동한 날 · 이어서 한 날(쉬는 날은 건너서 잇는다)")
        void 레벨과_연속() {
            planned.put(kid, Set.of(today.minusDays(1), today.minusDays(2), today.minusDays(3)));
            restDays.add(today.minusDays(2));
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today.minusDays(3), 5);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today.minusDays(1), 5);
            for (int i = 0; i < 4; i++) {
                recorder.sessionDone(finished(kid, UUID.randomUUID(), 1, Verification.TIMER, today.minusDays(1)));
            }

            ProgressView view = query.view(userId, kid);
            assertThat(view.xp()).isEqualTo(110);
            assertThat(view.level()).isEqualTo(2);
            assertThat(view.levelFloorXp()).isEqualTo(80);
            assertThat(view.nextLevelXp()).isEqualTo(200);
            assertThat(view.streakDays()).isEqualTo(2);
            assertThat(view.activeDays()).isEqualTo(2);
        }

        @Test
        @DisplayName("최근 줄 — 운동은 하루 한 줄(다 한 운동이 있으면 MISSION_DONE), 스티커 · 다시 재기는 한 건마다, 최근 것부터 다섯 줄")
        void 최근_줄() {
            UUID mission = UUID.randomUUID();
            move(kid, mission, 1, Phase.WARMUP, today.minusDays(2), 1);
            clock.step();
            recorder.sessionDone(finished(kid, mission, 2, Verification.TIMER, today.minusDays(2)));
            listener.on(cheer(CheerKind.PRAISE, mom, kid, "star", mission));
            listener.on(new FitnessTestRegistered(kid, UUID.randomUUID(), today.minusDays(40), null));
            clock.step();
            listener.on(remeasuredOn(today.minusDays(1)));
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 3);
            move(kid, UUID.randomUUID(), 1, Phase.MAIN, today, 3);
            listener.on(cheer(CheerKind.PRAISE, dad, kid, "crown", null));
            listener.on(cheer(CheerKind.PRAISE, dad, kid, "crown", null));

            List<XpLineView> lines = query.view(userId, kid).recentXp();
            assertThat(lines)
                    .extracting(
                            XpLineView::kind,
                            XpLineView::fromProfileId,
                            XpLineView::amount,
                            XpLineView::occurredOn,
                            XpLineView::reason)
                    .containsExactly(
                            tuple(XpKind.STICKER, dad, 10, today, "철수가 붙여 준 스티커"),
                            tuple(XpKind.STICKER, dad, 10, today, "철수가 붙여 준 스티커"),
                            tuple(XpKind.SESSION_DONE, null, 10, today, "운동을 했어요"),
                            tuple(XpKind.REMEASURE, null, 20, today.minusDays(1), "키와 몸무게를 다시 측정했어요"),
                            tuple(XpKind.STICKER, mom, 10, today, "은영이 붙여 준 스티커"));
            // 여섯째 줄(그제 운동 5 + 5 + 20)은 다섯 줄 밖이다
            assertThat(query.view(userId, kid).xp()).isEqualTo(90);
        }

        @Test
        @DisplayName("하루 운동 줄의 양은 그날 칸 · 미션을 다 더한다")
        void 하루_운동_줄의_합() {
            UUID mission = UUID.randomUUID();
            move(kid, mission, 1, Phase.WARMUP, today, 1);
            move(kid, mission, 2, Phase.MAIN, today, 1);
            clock.step();
            recorder.sessionDone(finished(kid, mission, 3, Verification.TIMER, today));
            assertThat(query.view(userId, kid).recentXp())
                    .extracting(XpLineView::kind, XpLineView::amount, XpLineView::occurredOn, XpLineView::reason)
                    .containsExactly(tuple(XpKind.MISSION_DONE, 35, today, "운동을 다 했어요"));
        }

        @Test
        @DisplayName("줄의 at 은 원장에 적은 시각이다 — 하루 운동 줄은 그날 가장 늦게 적은 시각, 최근 것부터")
        void 최근_줄의_시각() {
            UUID mission = UUID.randomUUID();
            move(kid, mission, 1, Phase.WARMUP, today.minusDays(1), 1);
            Instant yesterdayLast = clock.instant();
            move(kid, mission, 2, Phase.MAIN, today, 1);
            clock.step();
            recorder.sessionDone(finished(kid, mission, 3, Verification.TIMER, today));
            Instant todayLast = clock.instant();
            listener.on(cheer(CheerKind.PRAISE, mom, kid, "star", null));
            Instant stickerAt = clock.instant();

            assertThat(query.view(userId, kid).recentXp())
                    .extracting(XpLineView::kind, XpLineView::at)
                    .containsExactly(
                            tuple(XpKind.STICKER, stickerAt),
                            tuple(XpKind.MISSION_DONE, todayLast),
                            tuple(XpKind.SESSION_DONE, yesterdayLast));
        }

        @Test
        @DisplayName("스티커 줄은 붙인 사람을 읽는 사람의 말로 부른다 — 누가 읽든 보호자 · 형제는 프로필 이름, 가족에 없으면 「가족」")
        void 스티커_줄의_보낸_사람() {
            listener.on(cheer(CheerKind.PRAISE, UUID.randomUUID(), kid, "star", null));
            listener.on(cheer(CheerKind.PRAISE, sibling, kid, "star", null));
            listener.on(cheer(CheerKind.PRAISE, dad, kid, "star", null));
            listener.on(cheer(CheerKind.PRAISE, mom, kid, "star", null));
            listener.on(cheer(CheerKind.PRAISE, dad, mom, "star", null));

            assertThat(query.view(userId, kid).recentXp())
                    .extracting(XpLineView::reason)
                    .containsExactly("은영이 붙여 준 스티커", "철수가 붙여 준 스티커", "하린이 붙여 준 스티커", "가족이 붙여 준 스티커");
            assertThat(query.view(userId, mom).recentXp())
                    .extracting(XpLineView::reason)
                    .containsExactly("철수가 붙여 준 스티커");
        }

        @Test
        @DisplayName("같은 가족이 아니면 identity 의 예외가 그대로 올라간다")
        void 같은_가족이_아니면() {
            UUID stranger = UUID.randomUUID();
            when(familyAccess.requireSameFamilyAsProfile(stranger, kid)).thenThrow(new NotSameFamilyException());
            assertThatThrownBy(() -> query.view(stranger, kid)).isInstanceOf(NotSameFamilyException.class);
        }
    }

    /** 부를 때마다 멈춰 있다가 {@link #step} 으로 1초씩 나아가는 시계 — 원장 줄의 적은 차례를 가르려고 쓴다. */
    private static final class SteppingClock extends Clock {
        private final Instant[] now;
        private final ZoneId zone;

        SteppingClock(Instant start) {
            this(new Instant[] {start}, KST);
        }

        private SteppingClock(Instant[] now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void step() {
            now[0] = now[0].plus(Duration.ofSeconds(1));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new SteppingClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now[0];
        }
    }
}
