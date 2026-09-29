package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionMinutesAllocator;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * AI proposal → 제안 항목 변환(AI 인터페이스-명세 4장).
 * missions[i] → position=i, title, targetMetric=TIMER_MINUTES, rationale=reason(비었으면 copy.parent),
 * video=(day_offset, order) 순으로 첫 영상 있는 세션, participants=편성 대상 한 명(+ withParent 면 요청한 보호자),
 * citations=evidence 가 가리키는 것(없으면 전체), sessions=세션마다 칸 하나({@link #sessions}).
 * targetValue 는 칸이 있으면 칸 분의 합(보통 duration_min 과 같다), 칸이 없으면 duration_min(최소 1).
 * 유튜브 영상은 BE 카탈로그(exercise_videos)에 없어도 버리지 않는다 — 화면은 videoId 로 유튜브 구간을 튼다. 공단 영상(video.source = kspo)의
 * mp4 주소는 칸에 저장하지 않고 조회 때 카탈로그에서 붙이므로, 카탈로그에 없어 틀 수 없는 공단 영상(unplayableKspo)은 칸 · 대표 영상에서 뺀다
 * (칸은 영상 없이 남는다).
 */
public class ProposalConverter {
    public static final int MAX_TITLE = 120;
    public static final int MAX_COPY = 400;

    /** 칸 표의 video_id varchar(32) 에 들어가는 영상 id 글자(유튜브 id · 공단 파일 이름, 직접 만들기 검증과 같다). */
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    /** AI order 는 「그날 안의 차례」라 날마다 1부터 다시 센다. 날(day_offset)을 먼저 보고 order 로 세운다. 같으면 받은 차례. */
    private static final Comparator<CoachRunResult.Session> BY_DAY_AND_ORDER = Comparator.comparingInt(
                    (CoachRunResult.Session it) -> it.dayOffset() == null ? 0 : it.dayOffset())
            .thenComparing(CoachRunResult.Session::order, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Map<SessionPhase, String> PHASE_NAME =
            Map.of(SessionPhase.WARMUP, "준비운동", SessionPhase.MAIN, "본운동", SessionPhase.COOLDOWN, "정리운동");

    private final UUID subjectProfileId;
    private final ProfileRole subjectRole;
    private final @Nullable UUID companionProfileId;
    private final ClipTitles clipTitles;
    private final Set<String> unplayableKspo;

    public ProposalConverter(UUID subjectProfileId, ProfileRole subjectRole, @Nullable UUID companionProfileId) {
        this(subjectProfileId, subjectRole, companionProfileId, ClipTitles.none());
    }

    public ProposalConverter(
            UUID subjectProfileId, ProfileRole subjectRole, @Nullable UUID companionProfileId, ClipTitles clipTitles) {
        this(subjectProfileId, subjectRole, companionProfileId, clipTitles, Set.of());
    }

    /**
     * @param companionProfileId withParent 면 편성을 요청한 보호자(run.requestedBy), 아니면 null. AI 는 일간 미션에 동반자를 넣지 않으므로
     *     (ai:coach/compose.py) BE 가 붙인다(결정 2).
     * @param clipTitles 칸 영상 구간의 제목. 표 조회는 부르는 쪽이 한 번에 해 둔다
     * @param unplayableKspo 틀 수 없는 공단 영상 id(영상 표에 없거나 mp4 주소가 없다). 이 영상을 가리키는 칸은 영상 없이 둔다
     */
    public ProposalConverter(
            UUID subjectProfileId,
            ProfileRole subjectRole,
            @Nullable UUID companionProfileId,
            ClipTitles clipTitles,
            Set<String> unplayableKspo) {
        this.subjectProfileId = subjectProfileId;
        this.subjectRole = subjectRole;
        this.companionProfileId = companionProfileId;
        this.clipTitles = clipTitles;
        this.unplayableKspo = Set.copyOf(unplayableKspo);
    }

    public List<CoachProposalItem> convert(CoachRunResult.Proposal proposal) {
        List<CoachProposalItem> items = new ArrayList<>();
        List<CoachRunResult.Mission> missions = proposal.missions();
        for (int index = 0; index < missions.size(); index++) {
            CoachRunResult.Mission mission = missions.get(index);
            List<CoachRunResult.Session> sessions =
                    mission.sessions().stream().sorted(BY_DAY_AND_ORDER).toList();
            List<MissionSession> slots = sessions(sessions, targetMinutes(mission));
            Set<Integer> evidence = new LinkedHashSet<>();
            sessions.forEach(it -> evidence.addAll(it.evidence()));
            List<ProposalCitation> citations = proposal.citations().stream()
                    .filter(it -> evidence.isEmpty() || evidence.contains(it.index()))
                    .map(it -> new ProposalCitation(it.index(), it.label(), it.chunkId(), it.url()))
                    .toList();
            items.add(new CoachProposalItem(
                    index,
                    take(mission.title(), MAX_TITLE),
                    TargetMetric.TIMER_MINUTES.name(),
                    slots.isEmpty() ? targetMinutes(mission) : MissionSession.totalMinutes(slots),
                    take(rationale(mission), MAX_COPY),
                    take(description(sessions), MAX_COPY),
                    LocalDate.parse(mission.startDate()),
                    LocalDate.parse(mission.endDate()),
                    participants(mission),
                    firstVideo(sessions),
                    citations,
                    take(mission.copyChild(), MAX_COPY),
                    take(mission.copyParent(), MAX_COPY),
                    slots));
        }
        return List.copyOf(items);
    }

    /** 목표 분 = AI 가 준 그 회 운동 시간(요청한 분). 클립 길이의 합(video_sec)이 아니다. 없으면 1. */
    static int targetMinutes(CoachRunResult.Mission mission) {
        Integer minutes = mission.durationMin();
        return Math.max(minutes == null ? 0 : minutes, 1);
    }

    /** 「왜 이렇게 짰는지」 는 reason([n] 인용이 박힌 문장). 옛 응답처럼 비었으면 부모용 문구로 물러선다. */
    static String rationale(CoachRunResult.Mission mission) {
        return mission.reason().isBlank() ? mission.copyParent() : mission.reason();
    }

    /**
     * AI 가 준 참여자 중 편성 대상만 남긴다(응원 · 다른 구성원은 뺀다 — 응원 부모가 참여자가 되면 미션이 DONE 이 되지 않는다).
     * 대상이 든 미션이면 withParent 보호자를 동반자로 덧붙인다. 대상이 없는 미션은 참여자가 비어 승인 때 미션이 되지 않는다.
     */
    private List<ProposalParticipant> participants(CoachRunResult.Mission mission) {
        String subjectRef = ProfileRef.of(subjectProfileId);
        CoachRunResult.ParticipantRef subject = mission.participants().stream()
                .filter(it -> it.ref().equals(subjectRef))
                .findFirst()
                .orElse(null);
        if (subject == null) return List.of();
        List<ProposalParticipant> participants = new ArrayList<>();
        participants.add(new ProposalParticipant(
                subjectProfileId, subjectRole, subject.role().isBlank() ? CoachRoles.DRIVER : subject.role()));
        if (companionProfileId != null && !companionProfileId.equals(subjectProfileId)) {
            participants.add(new ProposalParticipant(companionProfileId, ProfileRole.PARENT, CoachRoles.COMPANION));
        }
        return List.copyOf(participants);
    }

    /**
     * 세션 → 칸. 이미 (day_offset, order) 로 세운 차례에 position 1..n 을 새로 매긴다(AI order 는 날마다 다시 센다).
     * phase 는 한글 단계({@link SessionPhase#fromKorean}), title 은 exercise_name(비면 영상 구간 제목, 그것도 없으면 단계 이름),
     * factor 는 fitness_factor(비었거나 모르는 이름이면 null), clip 은 video 의 사본, minutes 는 {@link SessionMinutesAllocator}.
     */
    List<MissionSession> sessions(List<CoachRunResult.Session> ordered, int minutes) {
        List<SessionPhase> phases =
                ordered.stream().map(it -> SessionPhase.fromKorean(it.phase())).toList();
        List<Integer> allocated = SessionMinutesAllocator.allocate(phases, minutes);
        List<MissionSession> slots = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            CoachRunResult.Session session = ordered.get(i);
            SessionPhase phase = phases.get(i);
            SessionClip clip = clipOf(session.video());
            slots.add(new MissionSession(
                    i + 1,
                    phase,
                    slotTitle(session.exerciseName(), clip, phase),
                    factorOf(session.fitnessFactor()),
                    allocated.get(i),
                    clip));
        }
        return List.copyOf(slots);
    }

    /** 영상 구간의 사본. 영상 id 가 칸 표에 맞지 않으면 붙이지 않는다. 끝이 시작보다 뒤가 아니면 끝을 버린다(영상 한 편). */
    private @Nullable SessionClip clipOf(CoachRunResult.@Nullable Video video) {
        if (video == null || !playable(video)) return null;
        int startSec = clipStart(video);
        Integer endSec = video.endSec();
        String title = clipTitles.titleOf(video.videoId(), startSec);
        return new SessionClip(
                video.videoId(),
                startSec,
                endSec != null && endSec > startSec ? endSec : null,
                title == null ? null : take(title, MAX_TITLE));
    }

    /** 칸 구간의 시작 초. 비었으면 영상 처음(0). 제목 조회 키(clipId)도 이 값으로 만든다. */
    static int clipStart(CoachRunResult.Video video) {
        Integer startSec = video.startSec();
        return startSec == null ? 0 : Math.max(0, startSec);
    }

    private static String slotTitle(String exerciseName, @Nullable SessionClip clip, SessionPhase phase) {
        if (!exerciseName.isBlank()) return take(exerciseName.strip(), MAX_TITLE);
        String clipTitle = clip == null ? null : clip.title();
        if (clipTitle != null && !clipTitle.isBlank()) return clipTitle;
        return PHASE_NAME.get(phase);
    }

    private static @Nullable FitnessFactor factorOf(String label) {
        if (label.isBlank()) return null;
        try {
            return FitnessFactor.fromLabel(label.strip());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String description(List<CoachRunResult.Session> sessions) {
        return sessions.stream()
                .map(it -> it.phase().isBlank() ? it.exerciseName() : it.phase() + " " + it.exerciseName())
                .collect(Collectors.joining(" · "));
    }

    /** 칸 표에 넣을 수 있고 틀 수 있는 영상인지. 영상 표에 없는 공단 영상은 화면이 유튜브로 틀려다 실패하므로 버린다. */
    private boolean playable(CoachRunResult.Video video) {
        if (!VIDEO_ID.matcher(video.videoId()).matches()) return false;
        return !(video.isKspo() && unplayableKspo.contains(video.videoId()));
    }

    private @Nullable ProposalVideo firstVideo(List<CoachRunResult.Session> sessions) {
        for (CoachRunResult.Session session : sessions) {
            CoachRunResult.Video video = session.video();
            if (video != null && !(video.isKspo() && unplayableKspo.contains(video.videoId()))) {
                return new ProposalVideo(video.videoId(), video.startSec());
            }
        }
        return null;
    }

    public static List<CoachStep> steps(CoachRunResult result) {
        return result.steps().stream()
                .map(it -> new CoachStep(it.seq(), it.name(), it.status(), it.summary()))
                .toList();
    }

    /** 요약 = 첫 미션의 부모용 문구, 없으면 단계 요약을 이어 붙인 것. */
    public static @Nullable String summary(CoachRunResult result) {
        CoachRunResult.Proposal proposal = result.proposal();
        if (proposal != null && !proposal.missions().isEmpty()) {
            String copyParent = proposal.missions().getFirst().copyParent();
            if (!copyParent.isBlank()) return copyParent;
        }
        String joined =
                result.steps().stream().map(CoachRunResult.Step::summary).collect(Collectors.joining(" · "));
        return joined.isBlank() ? null : joined;
    }

    private static String take(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }
}
