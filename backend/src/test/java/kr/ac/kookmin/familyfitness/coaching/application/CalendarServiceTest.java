package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.coaching.domain.CalendarRangeException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemorySessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 날짜별 기록 — 넣는 날 · 셈 · 권한 · 읽는 횟수. 규칙은 FE 목 src/mocks/history.ts(결정 44).
 * 기준 시각은 2026-09-09(수) 10:00 KST({@link Fixed}). 칸 끝은 실제 {@link SessionCompletionService} 를 그날 시각으로 돌려 쌓는다.
 */
class CalendarServiceTest {
    private static final LocalDate TODAY = Fixed.TODAY;
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D5 = LocalDate.of(2026, 9, 5);
    private static final LocalDate D7 = LocalDate.of(2026, 9, 7);
    private static final LocalDate D8 = LocalDate.of(2026, 9, 8);

    private final Family family = new Family();
    private final Family otherFamily = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, otherFamily);
    private final CountingActivity activity = new CountingActivity();
    private final CountingCompletions completions = new CountingCompletions();
    private final CountingMissions missions = new CountingMissions(completions);
    private final FakeCheers cheers = new FakeCheers();
    private final FakeRestDays restDays = new FakeRestDays();
    private final MissionCompletionPolicy policy =
            new MissionCompletionPolicy(activity, new InMemoryVideoInteractionRepository(), missions, completions);
    private final CalendarService service = new CalendarService(
            missions,
            completions,
            identity,
            activity,
            cheers,
            restDays,
            new InMemoryExerciseVideoRepository(),
            Fixed.time());

    private final UUID child = family.child.profileId();
    private final UUID parent = family.parent.profileId();

    /** 읽은 횟수를 세는 미션 저장소 — 날마다 · 미션마다 다시 읽지 않는지 본다. */
    static final class CountingMissions extends InMemoryMissionRepository {
        int overlappingCalls = 0;
        int findByIdCalls = 0;

        CountingMissions(InMemorySessionCompletionRepository completions) {
            super(completions);
        }

        @Override
        public List<Mission> findOverlapping(UUID familyId, LocalDate from, LocalDate to) {
            overlappingCalls++;
            return super.findOverlapping(familyId, from, to);
        }

        @Override
        public @Nullable Mission findById(UUID id) {
            findByIdCalls++;
            return super.findById(id);
        }
    }

    static final class CountingCompletions extends InMemorySessionCompletionRepository {
        int byMissionsCalls = 0;
        int byMissionCalls = 0;

        @Override
        public List<SessionCompletion> findByMissions(Collection<UUID> missionIds) {
            byMissionsCalls++;
            return super.findByMissions(missionIds);
        }

        @Override
        public List<SessionCompletion> findByMission(UUID missionId) {
            byMissionCalls++;
            return super.findByMission(missionId);
        }
    }

    static final class CountingActivity extends FakeActivity {
        int verifiedDaysCalls = 0;

        @Override
        public List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to) {
            verifiedDaysCalls++;
            return super.verifiedDays(profileId, from, to);
        }
    }

    /** 받은 응원. 받은 사람 · [from, to) 로 거른다(실제 CheerQuery 와 같다). */
    static final class FakeCheers implements CheerQuery {
        final List<CheerView> rows = new ArrayList<>();
        int receivedCalls = 0;

        @Override
        public int countCheers(UUID familyId, Instant from, Instant to) {
            return 0;
        }

        @Override
        public List<CheerView> received(UUID toProfileId, Instant from, Instant to) {
            receivedCalls++;
            return rows.stream()
                    .filter(it -> it.toProfileId().equals(toProfileId)
                            && !it.createdAt().isBefore(from)
                            && it.createdAt().isBefore(to))
                    .toList();
        }
    }

    static final class FakeRestDays implements RestDayQuery {
        final List<LocalDate> days = new ArrayList<>();
        int calls = 0;

        @Override
        public List<LocalDate> restDaysBetween(UUID familyId, LocalDate from, LocalDate to) {
            calls++;
            return days.stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .sorted()
                    .toList();
        }
    }

    /** 준비 1분 · 본 2분 · 정리 1분. 본운동 칸에만 영상 구간이 있다. */
    private static List<MissionSession> sessions() {
        return List.of(
                new MissionSession(1, SessionPhase.WARMUP, "제자리 걷기", null, 1, null),
                new MissionSession(2, SessionPhase.MAIN, "스쿼트", null, 2, new SessionClip("vid-squat", 30, 150, "스쿼트")),
                new MissionSession(3, SessionPhase.COOLDOWN, "숨 고르기", null, 1, null));
    }

    private Mission mission(String title, List<UUID> participants, LocalDate from, LocalDate to) {
        return missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                title,
                TargetMetric.TIMER_MINUTES,
                4,
                null,
                from,
                to,
                participants,
                sessions(),
                parent,
                Fixed.NOW));
    }

    /** 그날 10:00 KST 에 칸을 끝낸다. 계정 있는 아이는 제 계정으로, 나머지는 엄마 계정으로 보낸다. */
    private void complete(Mission mission, int position, UUID profileId, LocalDate day, int activeSeconds) {
        Instant at = day.atTime(10, 0).atZone(Fixed.ZONE).toInstant();
        SessionCompletionService sessions = new SessionCompletionService(
                missions, completions, identity, identity, activity, done -> 0, policy, event -> {}, Fixed.time(at));
        UUID user = profileId.equals(child) ? family.childUser : family.parentUser;
        sessions.complete(
                user,
                mission.getId(),
                position,
                new CompleteSessionCommand(profileId, activeSeconds, at.minusSeconds(activeSeconds + 60L), at));
    }

    private CalendarView calendarOf(UUID profileId, LocalDate from, LocalDate to) {
        return service.calendar(family.parentUser, family.familyId, profileId, from, to);
    }

    private static CalendarView.Day dayOf(CalendarView view, LocalDate date) {
        return view.days().stream()
                .filter(it -> it.date().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private static CheerView cheer(
            UUID to, UUID from, CheerKind kind, @Nullable String stickerId, @Nullable UUID missionId, Instant at) {
        return new CheerView(UUID.randomUUID(), from, "엄마", to, kind, "잘했어", stickerId, missionId, null, at);
    }

    @Test
    @DisplayName("하루짜리 — 끝낸 칸 · 분 · 잡힌 분. minutes 는 서버가 잰 초 ÷ 60 내림, 줄의 분은 끝낸 칸의 잡힌 분. 안 한 지난날도 0분으로 싣는다")
    void 하루짜리_운동() {
        Mission jump = mission("줄넘기", List.of(child, parent), D7, D7);
        complete(jump, 1, child, D7, 60);
        complete(jump, 2, child, D7, 90);
        mission("스트레칭", List.of(child), D8, D8);

        CalendarView view = calendarOf(child, D1, TODAY);

        assertThat(view.profileId()).isEqualTo(child);
        assertThat(view.days()).extracting(CalendarView.Day::date).containsExactly(D7, D8);
        CalendarView.Day d7 = dayOf(view, D7);
        assertThat(d7.minutes()).isEqualTo(2); // 60 + 90 = 150초
        assertThat(d7.plannedMinutes()).isEqualTo(4);
        assertThat(d7.rest()).isNull();
        CalendarView.Entry entry = d7.entries().getFirst();
        assertThat(entry.missionId()).isEqualTo(jump.getId());
        assertThat(entry.minutes()).isEqualTo(3); // 끝낸 칸의 잡힌 분 1 + 2
        assertThat(entry.completed()).isFalse();
        assertThat(entry.verifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS);
        assertThat(entry.sessions())
                .extracting(
                        CalendarView.Session::position,
                        CalendarView.Session::phase,
                        CalendarView.Session::minutes,
                        CalendarView.Session::done,
                        CalendarView.Session::verifiedBy)
                .containsExactly(
                        tuple(1, SessionPhase.WARMUP, 1, true, VerifiedBy.VIDEO_PROGRESS),
                        tuple(2, SessionPhase.MAIN, 2, true, VerifiedBy.VIDEO_PROGRESS),
                        tuple(3, SessionPhase.COOLDOWN, 1, false, null));
        CalendarView.Session squat = entry.sessions().get(1);
        assertThat(squat.clip()).isNotNull();
        assertThat(squat.clip().videoId()).isEqualTo("vid-squat");
        assertThat(squat.clip().startSec()).isEqualTo(30);
        assertThat(squat.clip().endSec()).isEqualTo(150);

        // 잡혀 있었는데 안 한 지난날 — 0분 · 잡힌 분은 그대로
        CalendarView.Day d8 = dayOf(view, D8);
        assertThat(d8.minutes()).isZero();
        assertThat(d8.plannedMinutes()).isEqualTo(4);
        assertThat(d8.entries().getFirst().minutes()).isZero();
        assertThat(d8.entries().getFirst().sessions())
                .extracting(CalendarView.Session::done)
                .containsOnly(false);

        // 같이 하기로 한 엄마에게 번진 칸 — 엄마 캘린더에도 끝낸 칸 · 분이 선다(결정 34)
        CalendarView mom = calendarOf(parent, D1, TODAY);
        assertThat(mom.days()).extracting(CalendarView.Day::date).containsExactly(D7);
        assertThat(dayOf(mom, D7).minutes()).isEqualTo(2);
        assertThat(dayOf(mom, D7).entries().getFirst().sessions())
                .extracting(CalendarView.Session::done)
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("다 한 하루짜리 — completed true, 칸 전부 done")
    void 다_한_하루짜리() {
        Mission jump = mission("줄넘기", List.of(child), D7, D7);
        complete(jump, 1, child, D7, 60);
        complete(jump, 2, child, D7, 120);
        complete(jump, 3, child, D7, 60);

        CalendarView.Entry entry =
                dayOf(calendarOf(child, D1, TODAY), D7).entries().getFirst();
        assertThat(entry.completed()).isTrue();
        assertThat(entry.minutes()).isEqualTo(4);
        assertThat(entry.sessions()).extracting(CalendarView.Session::done).containsOnly(true);
    }

    @Test
    @DisplayName("여러 날짜리 — 칸을 끝낸 날과 오늘에 선다. 칸 done 은 그날 끝낸 칸만, 다 했어요는 마지막 칸을 끝낸 날에만")
    void 여러_날짜리_운동() {
        Mission week = mission("이번 주 운동", List.of(child), D7, TODAY.plusDays(4));
        complete(week, 1, child, D7, 60);
        complete(week, 2, child, D8, 120);
        complete(week, 3, child, D8, 60);

        CalendarView view = calendarOf(child, D1, TODAY.plusDays(4));

        // 앞날(9/10~9/13)에는 운동을 싣지 않는다
        assertThat(view.days()).extracting(CalendarView.Day::date).containsExactly(D7, D8, TODAY);
        CalendarView.Entry d7 = dayOf(view, D7).entries().getFirst();
        assertThat(d7.sessions()).extracting(CalendarView.Session::done).containsExactly(true, false, false);
        assertThat(d7.minutes()).isEqualTo(1);
        assertThat(d7.completed()).isFalse();
        CalendarView.Entry d8 = dayOf(view, D8).entries().getFirst();
        assertThat(d8.sessions()).extracting(CalendarView.Session::done).containsExactly(false, true, true);
        assertThat(d8.sessions())
                .extracting(CalendarView.Session::verifiedBy)
                .containsExactly(null, VerifiedBy.VIDEO_PROGRESS, VerifiedBy.VIDEO_PROGRESS);
        assertThat(d8.minutes()).isEqualTo(3);
        assertThat(d8.completed()).isTrue();
        // 오늘(하는 중인 기간) — 오늘 끝낸 칸은 없고, 다 했어요는 9/8 에만
        CalendarView.Day today = dayOf(view, TODAY);
        assertThat(today.minutes()).isZero();
        assertThat(today.plannedMinutes()).isEqualTo(4);
        assertThat(today.entries().getFirst().completed()).isFalse();
        assertThat(today.entries().getFirst().sessions())
                .extracting(CalendarView.Session::done)
                .containsOnly(false);
    }

    @Test
    @DisplayName("여러 날짜리를 끝내 한 칸도 안 했으면 지난 마지막 날 하루로 선다")
    void 여러_날짜리_안_하고_지남() {
        mission("지난주 운동", List.of(child), D1, D5);

        CalendarView view = calendarOf(child, D1, TODAY);

        assertThat(view.days()).extracting(CalendarView.Day::date).containsExactly(D5);
        assertThat(dayOf(view, D5).plannedMinutes()).isEqualTo(4);
        assertThat(dayOf(view, D5).minutes()).isZero();
    }

    @Test
    @DisplayName("칸 없는 분 목표 — 칸은 null, 잡힌 분은 목표값, 줄의 분은 진행도 × 목표값")
    void 칸_없는_운동() {
        Mission walk = missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "걷기",
                TargetMetric.TIMER_MINUTES,
                10,
                null,
                D8,
                D8,
                List.of(child),
                List.of(),
                parent,
                Fixed.NOW));
        complete(walk, 1, child, D8, 600);

        CalendarView.Day d8 = dayOf(calendarOf(child, D1, TODAY), D8);
        assertThat(d8.plannedMinutes()).isEqualTo(10);
        assertThat(d8.minutes()).isEqualTo(10);
        CalendarView.Entry entry = d8.entries().getFirst();
        assertThat(entry.sessions()).isNull();
        assertThat(entry.minutes()).isEqualTo(10);
        assertThat(entry.completed()).isTrue();
    }

    @Test
    @DisplayName("걸음수 미션 — 그날 싣되 분은 0, 잡힌 분에 더하지 않는다")
    void 걸음수_미션() {
        missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "걸음수",
                TargetMetric.STEPS,
                3000,
                null,
                D8,
                D8,
                List.of(child),
                List.of(),
                parent,
                Fixed.NOW));

        CalendarView.Day d8 = dayOf(calendarOf(child, D1, TODAY), D8);
        assertThat(d8.plannedMinutes()).isNull();
        assertThat(d8.entries()).extracting(CalendarView.Entry::minutes).containsExactly(0);
    }

    @Test
    @DisplayName("형제 — 한 아이가 끝낸 칸은 같은 운동을 받은 다른 아이의 done 이 되지 않는다")
    void 형제는_저마다() {
        ProfileDetails sister = family.addChild("서아", LocalDate.of(2017, 2, 2));
        Mission both = mission("같이 하는 운동", List.of(child, sister.profileId()), D8, D8);
        complete(both, 1, child, D8, 60);

        CalendarView.Day d8 = dayOf(calendarOf(sister.profileId(), D1, TODAY), D8);
        assertThat(d8.minutes()).isZero();
        assertThat(d8.entries().getFirst().sessions())
                .extracting(CalendarView.Session::done)
                .containsOnly(false);
        assertThat(d8.entries().getFirst().minutes()).isZero();
    }

    @Test
    @DisplayName("쉬는 날은 rest true(앞날도), 앞날은 쉬는 날만 싣는다. 쉬는 날이 아니면 rest 를 싣지 않는다(null)")
    void 쉬는_날() {
        mission("앞날 운동", List.of(child), TODAY.plusDays(2), TODAY.plusDays(2));
        restDays.days.addAll(List.of(D8, TODAY.plusDays(3)));

        CalendarView view = calendarOf(child, D1, TODAY.plusDays(5));

        assertThat(view.days()).extracting(CalendarView.Day::date).containsExactly(D8, TODAY.plusDays(3));
        CalendarView.Day future = dayOf(view, TODAY.plusDays(3));
        assertThat(future.rest()).isTrue();
        assertThat(future.minutes()).isZero();
        assertThat(future.plannedMinutes()).isNull();
        assertThat(future.entries()).isEmpty();
        assertThat(future.stickers()).isEmpty();
    }

    @Test
    @DisplayName("스티커 — 받은 칭찬(PRAISE) 가운데 스티커가 붙은 것만, 붙인 시각의 KST 날짜에. 스티커만 있는 날도 싣는다")
    void 받은_스티커() {
        // 2026-09-07T15:30Z = 9/8 00:30 KST — 자정을 넘겨 붙였으니 9/8 에 선다
        Instant afterMidnight = Instant.parse("2026-09-07T15:30:00Z");
        UUID missionId = UUID.randomUUID();
        cheers.rows.add(cheer(child, parent, CheerKind.PRAISE, "star", missionId, afterMidnight));
        cheers.rows.add(cheer(child, parent, CheerKind.PRAISE, null, null, afterMidnight.plusSeconds(60)));
        cheers.rows.add(cheer(parent, child, CheerKind.THANKS, "heart", null, afterMidnight.plusSeconds(120)));
        cheers.rows.add(cheer(parent, child, CheerKind.DONE, null, missionId, afterMidnight.plusSeconds(180)));

        CalendarView view = calendarOf(child, D1, TODAY);

        assertThat(view.days()).extracting(CalendarView.Day::date).containsExactly(D8);
        CalendarView.Day d8 = dayOf(view, D8);
        assertThat(d8.minutes()).isZero();
        assertThat(d8.plannedMinutes()).isNull();
        assertThat(d8.entries()).isEmpty();
        assertThat(d8.stickers())
                .extracting(
                        CalendarView.Sticker::stickerId,
                        CalendarView.Sticker::fromProfileId,
                        CalendarView.Sticker::missionId,
                        CalendarView.Sticker::createdAt)
                .containsExactly(tuple("star", parent, missionId, afterMidnight));
        // 엄마가 받은 고마워요 스티커는 칭찬이 아니라 싣지 않는다(결정 44)
        assertThat(calendarOf(parent, D1, TODAY).days()).isEmpty();
    }

    @Test
    @DisplayName("움직인 기록만 있는 날도 싣는다 — 40초만 한 날은 0분이지만 빈 날이 아니다")
    void 움직인_기록만_있는_날() {
        activity.addActiveSeconds(child, D5, ActivitySource.TIMER, 40);
        activity.overwriteSteps(child, D7, 5000);

        CalendarView view = calendarOf(child, D1, TODAY);

        // 걸음수(MANUAL)는 서버가 잰 시간이 아니라 날을 세우지 않는다
        assertThat(view.days()).extracting(CalendarView.Day::date).containsExactly(D5);
        assertThat(dayOf(view, D5).minutes()).isZero();
        assertThat(dayOf(view, D5).plannedMinutes()).isNull();
    }

    @Test
    @DisplayName("권한 — 보호자는 식구 누구의 것이든, 자녀 계정은 자기 것만(403 NOT_A_PARENT), 다른 가족은 403 NOT_SAME_FAMILY")
    void 권한() {
        ProfileDetails sister = family.addChild("서아", LocalDate.of(2017, 2, 2));

        // 보호자: 아이 · 계정 없는 아이 · 다른 보호자
        service.calendar(family.parentUser, family.familyId, child, D1, TODAY);
        service.calendar(family.parentUser, family.familyId, sister.profileId(), D1, TODAY);
        service.calendar(family.parentUser, family.familyId, family.cheerParent.profileId(), D1, TODAY);
        // 자녀 계정: 자기 것은 되고 형제 · 부모 것은 안 된다
        service.calendar(family.childUser, family.familyId, child, D1, TODAY);
        NotAParentException sibling = assertThrows(
                NotAParentException.class,
                () -> service.calendar(family.childUser, family.familyId, sister.profileId(), D1, TODAY));
        assertThat(sibling.getCode()).isEqualTo("NOT_A_PARENT");
        assertThrows(
                NotAParentException.class,
                () -> service.calendar(family.childUser, family.familyId, parent, D1, TODAY));
        // 다른 가족: 남의 가족 주소 · 우리 가족 주소에 남의 아이
        NotSameFamilyException outsider = assertThrows(
                NotSameFamilyException.class,
                () -> service.calendar(otherFamily.parentUser, family.familyId, child, D1, TODAY));
        assertThat(outsider.getCode()).isEqualTo("NOT_SAME_FAMILY");
        assertThrows(
                NotSameFamilyException.class,
                () -> service.calendar(family.parentUser, family.familyId, otherFamily.child.profileId(), D1, TODAY));
        assertThrows(
                NotSameFamilyException.class,
                () -> service.calendar(otherFamily.parentUser, otherFamily.familyId, child, D1, TODAY));
    }

    @Test
    @DisplayName("기간 — 양끝 포함 42일까지. 43일 · 거꾸로 된 기간은 400 BAD_REQUEST")
    void 기간() {
        LocalDate from = TODAY.minusDays(41);
        assertThat(calendarOf(child, from, TODAY).from()).isEqualTo(from);

        CalendarRangeException tooLong =
                assertThrows(CalendarRangeException.class, () -> calendarOf(child, TODAY.minusDays(42), TODAY));
        assertThat(tooLong.getCode()).isEqualTo("BAD_REQUEST");
        assertThrows(CalendarRangeException.class, () -> calendarOf(child, TODAY, TODAY.minusDays(1)));
    }

    @Test
    @DisplayName("기간 — 날짜로 셈할 수 있는 1900-01-01 ~ 2100-12-31 밖이면 400 BAD_REQUEST(날을 더하다 연도가 넘쳐 500 이 나던 곳)")
    void 셈할_수_없는_날짜는_400() {
        CalendarRangeException farFuture = assertThrows(
                CalendarRangeException.class, () -> calendarOf(child, LocalDate.of(999_999_999, 12, 1), LocalDate.MAX));
        assertThat(farFuture.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(farFuture.getMessage()).contains("1900-01-01").contains("2100-12-31");
        assertThrows(
                CalendarRangeException.class,
                () -> calendarOf(child, LocalDate.of(2100, 12, 31), LocalDate.of(2101, 1, 1)));
        assertThrows(
                CalendarRangeException.class,
                () -> calendarOf(child, LocalDate.of(1899, 12, 31), LocalDate.of(1900, 1, 1)));

        assertThat(calendarOf(child, LocalDate.of(2100, 12, 1), LocalDate.of(2100, 12, 31))
                        .days())
                .isEmpty();
        assertThat(calendarOf(child, LocalDate.of(1900, 1, 1), LocalDate.of(1900, 1, 31))
                        .days())
                .isEmpty();
    }

    @Test
    @DisplayName("42일 · 미션 여럿이어도 미션 · 칸 끝 · 활동 · 응원 · 쉬는 날을 한 번씩 읽고, 진행도를 다시 저장하지 않는다")
    void 한_번씩_읽는다() {
        LocalDate from = TODAY.minusDays(35);
        LocalDate to = TODAY.plusDays(6);
        for (int i = 0; i < 5; i++) {
            Mission m = mission("운동 " + i, List.of(child, parent), D1.plusDays(i), D1.plusDays(i));
            complete(m, 1, child, D1.plusDays(i), 60);
        }
        Mission week = mission("이번 주 운동", List.of(child), D7, to);
        complete(week, 1, child, D7, 60);
        complete(week, 2, child, D8, 120);
        cheers.rows.add(cheer(child, parent, CheerKind.PRAISE, "star", null, Fixed.NOW));
        restDays.days.add(TODAY.plusDays(3));
        int savedBefore = missions.saveCount;
        missions.overlappingCalls = 0;
        missions.findByIdCalls = 0;
        completions.byMissionsCalls = 0;
        completions.byMissionCalls = 0;
        activity.verifiedDaysCalls = 0;

        CalendarView view = calendarOf(child, from, to);

        assertThat(view.days()).hasSize(9); // 9/1~9/5 · 9/7 · 9/8 · 9/9 · 9/12(쉬는 날)
        assertThat(missions.overlappingCalls).isEqualTo(1);
        assertThat(missions.findByIdCalls).isZero();
        assertThat(completions.byMissionsCalls).isEqualTo(1);
        assertThat(completions.byMissionCalls).isZero();
        assertThat(activity.verifiedDaysCalls).isEqualTo(1);
        assertThat(cheers.receivedCalls).isEqualTo(1);
        assertThat(restDays.calls).isEqualTo(1);
        assertThat(missions.saveCount).isEqualTo(savedBefore);
    }
}
