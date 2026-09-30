package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyCreated;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyDays;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 심사용 체험 가족의 지난 2주 운동 기록을 넣는다. 날마다 무엇을 했는지는 {@link ReviewFamilyDays} 에 있다.
 *
 * <p>행을 직접 넣지 않고, 보호자가 화면에서 부르는 것과 같은 {@link MissionService#createAll} 과
 * {@link SessionCompletionService#complete} 를 거친다. 다만 그날 그 시각을 가리키는 시계로 서비스를 새로 만들어 부른다. 미션은
 * 오늘부터만 만들 수 있고 칸도 기간 안에서만 끝낼 수 있어서다. 그래서 칸 끝 기록, 활동 분, 경험치, 연속 기록, 업적, 리그 달성률의
 * 재료가 사람이 2주 동안 쓴 것과 똑같이 쌓인다.
 *
 * <p>칸의 영상은 실제로 실린 클립 표({@link ExerciseClipRepository#findAllActive})에서 그 아이 연령대에 맞는 운동 구간만 고른다.
 * 공단 영상과 유튜브 구간을 섞고, 며칠 전인지로 차례를 돌려 날마다 다른 영상이 나온다. 네 식구가 함께 하는 미션은 가장 어린 아이
 * 연령대의 클립을 쓴다.
 *
 * <p>쉬는 날 카드(activity)와 측정(fitness)보다 늦게 돈다({@link Order}). 오늘 미션은 지금 시계로 만든다. 하윤 것은 끝내지 않고
 * 두어 아이 화면에 「오늘 운동」 이 남고, 서준 것은 지금 끝낸 것으로 둔다.
 */
@Component
public class ReviewFamilyMissions {
    static final LocalTime CREATED_AT = LocalTime.of(7, 0);
    static final LocalTime KIDS_START = LocalTime.of(18, 30);
    static final LocalTime FAMILY_START = LocalTime.of(19, 30);

    /** 칸 분. 준비, 본운동 둘, 정리. 운동할 수 있는 시간(하윤 20분, 서준 10분)에 맞췄다. */
    static final List<Integer> YOUTH_MINUTES = List.of(3, 7, 7, 3);

    static final List<Integer> TODDLER_MINUTES = List.of(2, 3, 3, 2);
    static final List<Integer> FAMILY_MINUTES = List.of(3, 7, 7, 3);

    static final String FAMILY_TITLE = "온 가족 함께 운동";

    private final MissionRepository missions;
    private final SessionCompletionRepository completions;
    private final ExerciseVideoRepository videos;
    private final ExerciseClipRepository clips;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final ActivityRecorder activity;
    private final ProgressRecorder progress;
    private final MissionCompletionPolicy policy;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;
    private final TransactionTemplate readApart;

    /** 켜진 클립 표. 표는 마이그레이션으로만 바뀌어 한 번 읽고 계속 쓴다. */
    private volatile @Nullable List<ExerciseClip> catalog;

    public ReviewFamilyMissions(
            MissionRepository missions,
            SessionCompletionRepository completions,
            ExerciseVideoRepository videos,
            ExerciseClipRepository clips,
            FamilyAccess familyAccess,
            ProfileQuery profiles,
            ActivityRecorder activity,
            ProgressRecorder progress,
            MissionCompletionPolicy policy,
            ApplicationEventPublisher events,
            Clock clock,
            ZoneId appZone,
            PlatformTransactionManager transactions) {
        this.missions = missions;
        this.completions = completions;
        this.videos = videos;
        this.clips = clips;
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.activity = activity;
        this.progress = progress;
        this.policy = policy;
        this.events = events;
        this.clock = clock;
        this.zone = appZone;
        this.readApart = new TransactionTemplate(transactions);
        this.readApart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readApart.setReadOnly(true);
    }

    @EventListener
    @Order(30)
    public void on(ReviewFamilyCreated created) {
        UUID guardian = created.guardianUserId();
        List<ProfileSummary> family = profiles.summariesOfFamily(created.familyId());
        ProfileSummary youth = member(family, created.youthProfileId());
        ProfileSummary toddler = member(family, created.toddlerProfileId());
        List<UUID> everyone = family.stream().map(ProfileSummary::profileId).toList();
        List<ExerciseClip> catalog = catalog();
        ClipShelf youthShelf = ClipShelf.of(catalog, youth.ageGroup());
        ClipShelf toddlerShelf = ClipShelf.of(catalog, toddler.ageGroup());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        for (int ago = ReviewFamilyDays.HISTORY_DAYS; ago >= 1; ago--) {
            if (ago == ReviewFamilyDays.REST_DAY) continue;
            LocalDate day = today.minusDays(ago);
            if (ReviewFamilyDays.FAMILY_DAYS.contains(ago)) {
                UUID together = create(
                        at(day, CREATED_AT),
                        guardian,
                        created.familyId(),
                        FAMILY_TITLE,
                        everyone,
                        toddlerShelf.sessions(ago, FAMILY_MINUTES));
                Instant end = completeAll(at(day, FAMILY_START), guardian, together, youth, FAMILY_MINUTES);
                completeAll(end, guardian, together, toddler, FAMILY_MINUTES);
                continue;
            }
            UUID mine = create(
                    at(day, CREATED_AT),
                    guardian,
                    created.familyId(),
                    titleOf(youth, day),
                    List.of(youth.profileId()),
                    youthShelf.sessions(ago, YOUTH_MINUTES));
            UUID his = create(
                    at(day, CREATED_AT),
                    guardian,
                    created.familyId(),
                    titleOf(toddler, day),
                    List.of(toddler.profileId()),
                    toddlerShelf.sessions(ago, TODDLER_MINUTES));
            Instant start = at(day, KIDS_START);
            if (ago != ReviewFamilyDays.TODDLER_MISSED_DAY) {
                start = completeAll(start, guardian, his, toddler, TODDLER_MINUTES);
            }
            if (ago != ReviewFamilyDays.YOUTH_MISSED_DAY) completeAll(start, guardian, mine, youth, YOUTH_MINUTES);
        }

        Instant now = clock.instant();
        create(
                now,
                guardian,
                created.familyId(),
                titleOf(youth, today),
                List.of(youth.profileId()),
                youthShelf.sessions(0, YOUTH_MINUTES));
        UUID his = create(
                now,
                guardian,
                created.familyId(),
                titleOf(toddler, today),
                List.of(toddler.profileId()),
                toddlerShelf.sessions(0, TODDLER_MINUTES));
        doneNow(guardian, his, toddler, TODDLER_MINUTES);
    }

    /**
     * 클립 표를 따로 연 읽기 트랜잭션에서 읽는다. 로그인 트랜잭션에서 읽으면 클립과 영상 행 수천 개가 그 트랜잭션의 영속 컨텍스트에
     * 남아, 뒤에 도는 조회마다 그 행을 모두 훑느라 로그인 한 번이 몇 분씩 걸렸다.
     */
    private List<ExerciseClip> catalog() {
        List<ExerciseClip> loaded = catalog;
        if (loaded != null) return loaded;
        loaded = readApart.execute(status -> clips.findAllActive());
        catalog = loaded;
        return loaded == null ? List.of() : loaded;
    }

    private static ProfileSummary member(List<ProfileSummary> family, UUID profileId) {
        return family.stream()
                .filter(it -> it.profileId().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("체험 가족에 없는 프로필: " + profileId));
    }

    /** 평일은 저녁 운동, 주말은 주말 운동. */
    static String titleOf(ProfileSummary child, LocalDate day) {
        boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
        return child.name() + (weekend ? " 주말 운동" : " 저녁 운동");
    }

    private Instant at(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(zone).toInstant();
    }

    private AppTime fixedAt(Instant instant) {
        return new AppTime(Clock.fixed(instant, zone), zone);
    }

    /** {@code createdAt} 시각에 보호자가 만든 것으로 둔다. 그날 하루짜리 미션이다. */
    private UUID create(
            Instant createdAt,
            UUID guardian,
            UUID familyId,
            String title,
            List<UUID> participants,
            List<MissionSession> sessions) {
        LocalDate day = LocalDate.ofInstant(createdAt, zone);
        MissionService service = new MissionService(
                missions, completions, videos, familyAccess, profiles, policy, events, fixedAt(createdAt));
        CreateMissionCommand command = new CreateMissionCommand(
                title,
                day,
                day,
                TargetMetric.TIMER_MINUTES,
                MissionSession.totalMinutes(sessions),
                null,
                participants,
                sessions);
        return service.createAll(guardian, familyId, List.of(command)).missionId();
    }

    /** {@code start} 부터 칸을 차례로 끝낸다. 칸마다 그 칸이 끝난 시각의 시계로 부른다. 마지막 칸이 끝난 시각. */
    private Instant completeAll(
            Instant start, UUID guardian, UUID missionId, ProfileSummary child, List<Integer> minutes) {
        Instant cursor = start;
        for (int i = 0; i < minutes.size(); i++) {
            Instant end = cursor.plusSeconds(minutes.get(i) * 60L);
            completion(fixedAt(end))
                    .complete(
                            guardian,
                            missionId,
                            i + 1,
                            new CompleteSessionCommand(child.profileId(), minutes.get(i) * 60, cursor, end));
            cursor = end.plusSeconds(60);
        }
        return cursor;
    }

    /** 오늘 칸은 지금 시계로 끝낸다. 칸마다 지금까지 그 칸 시간만큼 운동한 것으로 보낸다. */
    private void doneNow(UUID guardian, UUID missionId, ProfileSummary child, List<Integer> minutes) {
        SessionCompletionService service = completion(new AppTime(clock, zone));
        for (int i = 0; i < minutes.size(); i++) {
            Instant end = clock.instant();
            int seconds = minutes.get(i) * 60;
            service.complete(
                    guardian,
                    missionId,
                    i + 1,
                    new CompleteSessionCommand(child.profileId(), seconds, end.minusSeconds(seconds), end));
        }
    }

    private SessionCompletionService completion(AppTime time) {
        return new SessionCompletionService(
                missions, completions, familyAccess, profiles, activity, progress, policy, events, time);
    }

    /**
     * 한 연령대가 볼 수 있는 운동 클립을 단계마다 공단 영상과 유튜브 구간으로 나눠 둔다. 공단 영상은 AI 표의 줄마다 같은 클립이 여러
     * 번 나오므로 clipId 로 한 번만 둔다.
     */
    record ClipShelf(Map<SessionPhase, List<ExerciseClip>> kspo, Map<SessionPhase, List<ExerciseClip>> youtube) {
        static ClipShelf of(List<ExerciseClip> catalog, AgeGroup group) {
            Map<SessionPhase, Map<String, ExerciseClip>> kspo = new LinkedHashMap<>();
            Map<SessionPhase, Map<String, ExerciseClip>> youtube = new LinkedHashMap<>();
            for (ExerciseClip clip : catalog) {
                if (!clip.active() || !clip.isExercise() || !clip.suits(group)) continue;
                Map<SessionPhase, Map<String, ExerciseClip>> side = clip.media().mediaUrl() != null ? kspo : youtube;
                side.computeIfAbsent(clip.phase(), k -> new LinkedHashMap<>()).putIfAbsent(clip.clipId(), clip);
            }
            if (kspo.isEmpty() && youtube.isEmpty()) {
                throw new IllegalStateException(group + " 연령대에 맞는 운동 클립이 클립 표에 없다");
            }
            return new ClipShelf(listed(kspo), listed(youtube));
        }

        private static Map<SessionPhase, List<ExerciseClip>> listed(
                Map<SessionPhase, Map<String, ExerciseClip>> byPhase) {
            Map<SessionPhase, List<ExerciseClip>> out = new LinkedHashMap<>();
            byPhase.forEach((phase, clips) -> out.put(phase, List.copyOf(clips.values())));
            return out;
        }

        /**
         * 준비, 본운동 둘, 정리 칸. {@code ago}(며칠 전)로 차례를 돌려 날마다 다른 클립이 나온다. 본운동 첫 칸은 공단 영상, 둘째 칸은
         * 유튜브 구간을 먼저 찾고, 준비와 정리는 날마다 번갈아 찾는다. 찾는 쪽에 없으면 다른 쪽에서, 그 단계에 아예 없으면 본운동
         * 클립에서 고른다. 한 미션 안에서 같은 클립은 두 번 넣지 않는다(고를 것이 모자랄 때만 겹친다).
         */
        List<MissionSession> sessions(int ago, List<Integer> minutes) {
            boolean kspoFirst = ago % 2 == 0;
            List<SessionPhase> phases =
                    List.of(SessionPhase.WARMUP, SessionPhase.MAIN, SessionPhase.MAIN, SessionPhase.COOLDOWN);
            List<Boolean> preferKspo = List.of(kspoFirst, true, false, !kspoFirst);
            List<Integer> turns = List.of(ago, ago * 2, ago * 2 + 1, ago);
            List<String> used = new ArrayList<>();
            List<MissionSession> out = new ArrayList<>();
            for (int i = 0; i < phases.size(); i++) {
                ExerciseClip clip = pick(phases.get(i), preferKspo.get(i), turns.get(i), used);
                used.add(clip.clipId());
                out.add(new MissionSession(
                        i + 1,
                        phases.get(i),
                        clip.title(),
                        clip.factor(),
                        minutes.get(i),
                        new SessionClip(clip.videoId(), clip.startSec(), clip.endSec(), clip.title())));
            }
            return out;
        }

        private ExerciseClip pick(SessionPhase phase, boolean preferKspo, int turn, List<String> used) {
            List<ExerciseClip> first = (preferKspo ? kspo : youtube).getOrDefault(phase, List.of());
            List<ExerciseClip> second = (preferKspo ? youtube : kspo).getOrDefault(phase, List.of());
            List<ExerciseClip> pool = !first.isEmpty() ? first : second;
            if (pool.isEmpty()) {
                pool = new ArrayList<>(kspo.getOrDefault(SessionPhase.MAIN, List.of()));
                pool.addAll(youtube.getOrDefault(SessionPhase.MAIN, List.of()));
            }
            if (pool.isEmpty()) {
                pool = kspo.values().stream().flatMap(List::stream).toList();
                if (pool.isEmpty())
                    pool = youtube.values().stream().flatMap(List::stream).toList();
            }
            for (int step = 0; step < pool.size(); step++) {
                ExerciseClip clip = pool.get(Math.floorMod(turn + step, pool.size()));
                if (!used.contains(clip.clipId())) return clip;
            }
            return pool.get(Math.floorMod(turn, pool.size()));
        }
    }
}
