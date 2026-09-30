package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CalendarRangeException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionDay;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 사람의 날짜별 기록(GET /families/{familyId}/calendar). 넣는 날 · 셈은 FE 목 src/mocks/history.ts 그대로다(결정 44).
 *
 * <pre>
 * 넣는 날     그날 선 운동 · 움직인 기록 · 받은 스티커 · 쉬는 날 중 하나라도 있는 날(목 :250). 앞날(오늘 뒤)은 운동을 싣지 않는다(목 :195)
 * 선 운동     이 사람이 참여자인 미션 가운데 {@link MissionSpan#calendarDays} 가 그날을 내는 것 — 잡힌 날 · 리그와 같은 규칙
 * minutes    그날 서버가 잰 활동(TIMER + VIDEO) 초 ÷ 60 내림(결정 37). 리그의 「해낸 날」 과 같은 값을 읽는다
 * stickers   이 사람이 받은 칭찬(PRAISE) 가운데 스티커가 붙은 것, 붙인 시각의 KST 날짜(목 stickersOn :209-221)
 * rest       가족 쉬는 날. 앞날도 싣는다
 * </pre>
 *
 * 읽기만 한다 — 진행도를 다시 계산해 저장하는 {@link MissionCompletionPolicy} 를 부르지 않는다. 날 수 · 미션 수와 상관없이 미션
 * (참여자 · 칸 포함) · 칸 끝 · 활동 · 응원 · 쉬는 날을 한 번씩(IN) 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {
    /** 한 번에 받는 날 수 상한(양끝 포함). FE 요청서 3장 · ASKS 0-2. */
    public static final int MAX_DAYS = 42;

    /**
     * 받는 날짜의 범위(양끝 포함). LocalDate 형식만 맞으면 +999999999년도 들어와, 받은 스티커를 셀 때 끝날에 하루를 더하다 연도가
     * 넘쳐 500 이 났다(SA-16). 사람 · 운동 기록이 있을 수 없는 날이라 400 으로 막는다.
     */
    static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);

    static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    private static final Comparator<Mission> MISSION_ORDER = Comparator.comparing(Mission::getStartsOn)
            .thenComparing(Mission::getCreatedAt)
            .thenComparing(Mission::getId);

    private final MissionRepository missions;
    private final SessionCompletionRepository completions;
    private final FamilyAccess familyAccess;
    private final ActivityQuery activity;
    private final CheerQuery cheers;
    private final RestDayQuery restDays;
    private final AppTime time;

    public CalendarService(
            MissionRepository missions,
            SessionCompletionRepository completions,
            FamilyAccess familyAccess,
            ActivityQuery activity,
            CheerQuery cheers,
            RestDayQuery restDays,
            AppTime time) {
        this.missions = missions;
        this.completions = completions;
        this.familyAccess = familyAccess;
        this.activity = activity;
        this.cheers = cheers;
        this.restDays = restDays;
        this.time = time;
    }

    public CalendarView calendar(UUID userId, UUID familyId, UUID profileId, LocalDate from, LocalDate to) {
        requireRange(from, to);
        requireViewer(userId, familyId, profileId);
        LocalDate today = time.today();
        LocalDate lastPast = to.isAfter(today) ? today : to;
        boolean hasPast = !lastPast.isBefore(from);

        Map<LocalDate, List<MissionDay>> planned =
                hasPast ? missionDays(familyId, profileId, from, lastPast, today) : Map.of();
        Map<LocalDate, Integer> moved = hasPast ? movedMinutes(profileId, from, lastPast) : Map.of();
        Map<LocalDate, List<CalendarView.Sticker>> stickers = stickersOf(profileId, from, to);
        Set<LocalDate> rest = new HashSet<>(restDays.restDaysBetween(familyId, from, to));

        List<CalendarView.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<MissionDay> stood = planned.getOrDefault(date, List.of());
            List<CalendarView.Sticker> got = stickers.getOrDefault(date, List.of());
            boolean isRest = rest.contains(date);
            if (stood.isEmpty() && !moved.containsKey(date) && got.isEmpty() && !isRest) continue;
            days.add(new CalendarView.Day(
                    date,
                    moved.getOrDefault(date, 0),
                    plannedMinutes(stood),
                    stood.stream().map(CalendarService::entryOf).toList(),
                    got,
                    isRest ? Boolean.TRUE : null));
        }
        return new CalendarView(profileId, from, to, days);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (outOfBounds(from) || outOfBounds(to)) throw new CalendarRangeException(from, to, EARLIEST, LATEST);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new CalendarRangeException(from, to, MAX_DAYS);
        }
    }

    private static boolean outOfBounds(LocalDate date) {
        return date.isBefore(EARLIEST) || date.isAfter(LATEST);
    }

    /**
     * 보는 사람. 같은 가족이 아니면 403 NOT_SAME_FAMILY. 보호자 계정은 식구 누구의 것이든 본다(아이 모드로 계정 없는 아이를
     * 볼 때도 보호자 권한이면 된다). 자녀 계정은 자기 것만 — 형제의 기록을 나란히 보지 않게(FE 규칙 10, 결정 44).
     */
    private void requireViewer(UUID userId, UUID familyId, UUID profileId) {
        ProfileSummary caller = familyAccess.requireMember(userId, familyId);
        ProfileSummary target = familyAccess.requireSameFamilyAsProfile(userId, profileId);
        if (!target.familyId().equals(familyId)) throw new NotSameFamilyException();
        if (!caller.isParent() && !caller.profileId().equals(target.profileId())) {
            throw new NotAParentException("다른 식구의 기록은 보호자만 볼 수 있습니다");
        }
    }

    /** 날짜마다 그날 선 운동. 가족 미션(참여자 · 칸 포함)과 이 사람의 칸 끝 기록을 한 번씩 읽는다. */
    private Map<LocalDate, List<MissionDay>> missionDays(
            UUID familyId, UUID profileId, LocalDate from, LocalDate to, LocalDate today) {
        List<Mission> mine = missions.findOverlapping(familyId, from, to).stream()
                .filter(it -> it.isParticipant(profileId))
                .sorted(MISSION_ORDER)
                .toList();
        if (mine.isEmpty()) return Map.of();
        Map<UUID, Map<Integer, SessionCompletion>> doneByMission = new HashMap<>();
        completions.findByMissions(mine.stream().map(Mission::getId).toList()).stream()
                .filter(it -> it.profileId().equals(profileId))
                .forEach(it -> doneByMission
                        .computeIfAbsent(it.missionId(), k -> new HashMap<>())
                        .put(it.position(), it));

        Map<LocalDate, List<MissionDay>> out = new HashMap<>();
        for (Mission mission : mine) {
            MissionParticipant me = mission.participantOf(profileId);
            Map<Integer, SessionCompletion> done = doneByMission.getOrDefault(mission.getId(), Map.of());
            MissionSpan span = MissionSpan.of(
                    mission.getStartsOn(),
                    mission.getEndsOn(),
                    mission.getTargetMetric(),
                    me.isCompleted(),
                    me.getProgress() > 0,
                    me.getVerifiedAt(),
                    done.values().stream().map(SessionCompletion::completedOn).collect(Collectors.toSet()),
                    time.getZone());
            for (LocalDate day : span.calendarDays(today)) {
                if (day.isBefore(from) || day.isAfter(to)) continue;
                out.computeIfAbsent(day, k -> new ArrayList<>()).add(new MissionDay(mission, me, done, span, day));
            }
        }
        return out;
    }

    /** 움직인 날(서버가 잰 초 &gt; 0)과 그날 분. 40초만 한 날도 넣는다(분은 0). */
    private Map<LocalDate, Integer> movedMinutes(UUID profileId, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> out = new HashMap<>();
        for (DailyMinutes it : activity.verifiedDays(profileId, from, to)) out.put(it.date(), it.minutes());
        return out;
    }

    /** 받은 칭찬 스티커를 붙인 시각의 KST 날짜로 나눈다. 받은 응원을 기간째 한 번 읽는다. */
    private Map<LocalDate, List<CalendarView.Sticker>> stickersOf(UUID profileId, LocalDate from, LocalDate to) {
        Map<LocalDate, List<CalendarView.Sticker>> out = new HashMap<>();
        cheers.received(profileId, time.startOfDay(from), time.startOfDay(to.plusDays(1))).stream()
                .filter(it -> it.kind() == CheerKind.PRAISE && it.stickerId() != null)
                .forEach(it -> out.computeIfAbsent(time.dateOf(it.createdAt()), k -> new ArrayList<>())
                        .add(new CalendarView.Sticker(
                                it.cheerId(),
                                Objects.requireNonNull(it.stickerId()),
                                it.fromProfileId(),
                                it.fromName(),
                                it.message(),
                                it.missionId(),
                                it.createdAt())));
        return out;
    }

    /** 선 운동의 잡힌 분 합. 선 운동이 없거나 합이 0 이면 null(목 {@code planned || null}). */
    private static @Nullable Integer plannedMinutes(List<MissionDay> stood) {
        int sum = stood.stream().mapToInt(MissionDay::plannedMinutes).sum();
        return sum > 0 ? sum : null;
    }

    private static CalendarView.Entry entryOf(MissionDay day) {
        Mission mission = day.mission();
        return new CalendarView.Entry(
                mission.getId(),
                mission.getTitle(),
                day.minutes(),
                day.me().getVerifiedBy(),
                day.completed(),
                mission.hasSessions()
                        ? mission.getSessions().stream()
                                .map(it -> sessionOf(day, it))
                                .toList()
                        : null);
    }

    private static CalendarView.Session sessionOf(MissionDay day, MissionSession session) {
        return new CalendarView.Session(
                session.position(),
                session.phase(),
                session.title(),
                session.minutes(),
                MissionSessionView.of(session).clip(),
                day.verifiedByOf(session),
                day.isDone(session));
    }
}
