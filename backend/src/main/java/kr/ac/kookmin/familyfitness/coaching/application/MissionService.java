package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionCompletions;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoMedia;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 미션 직접 만들기(한 건 · 여러 날) · 목록 · 단건 · 보호자 확인. 지우기는 {@link MissionDeletionService}, 느낌은 {@link MissionFeedbackService}. */
@Service
public class MissionService {
    private final MissionRepository missions;
    private final SessionCompletionRepository completions;
    private final ExerciseVideoRepository videos;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final MissionCompletionPolicy policy;
    private final ApplicationEventPublisher events;
    private final AppTime time;

    public MissionService(
            MissionRepository missions,
            SessionCompletionRepository completions,
            ExerciseVideoRepository videos,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            MissionCompletionPolicy policy,
            ApplicationEventPublisher events,
            AppTime time) {
        this.missions = missions;
        this.completions = completions;
        this.videos = videos;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.policy = policy;
        this.events = events;
        this.time = time;
    }

    /** 미션 한 건 직접 만들기. 규칙은 {@link #createAll}. */
    @Transactional
    public MissionCreatedView create(UUID userId, UUID familyId, CreateMissionCommand command) {
        return createAll(userId, familyId, List.of(command));
    }

    /**
     * 보호자가 직접 만든다(한 건 또는 여러 날 dates[] — 날마다 한 건). 판단 차례:
     * <ol>
     *   <li>보호자가 아니면 403 NOT_A_PARENT
     *   <li>어느 한 건이라도 시작일이 오늘(KST)보다 앞이면 422 INVALID_DATE(결정 40)
     *   <li>참여자가 이 가족이 아니면 422 NOT_FAMILY_MEMBER, 동의가 필요한데 없으면 422 CONSENT_REQUIRED
     *   <li>미션 영상이 카탈로그에 없으면 404 VIDEO_NOT_FOUND(칸의 영상 id 는 보지 않는다 — 칸은 영상 구간의 사본을 든다)
     *   <li>칸 규칙(목표 분 = 칸 분 합 등)은 {@link Mission#manual} 이 400 으로 막는다
     * </ol>
     * 전부 되거나 전부 안 된다 — 모든 건을 먼저 만들어 검사한 뒤에 저장하고, 한 트랜잭션이라 저장 중 실패해도 되돌려진다.
     * 저장한 미션마다 {@link kr.ac.kookmin.familyfitness.coaching.api.MissionCreated} 를 낸다(결정 48).
     */
    @Transactional
    public MissionCreatedView createAll(UUID userId, UUID familyId, List<CreateMissionCommand> commands) {
        if (commands.isEmpty()) throw new IllegalArgumentException("만들 운동이 없습니다");
        ProfileSummary parent = familyAccess.requireParent(userId, familyId);
        LocalDate today = time.today();
        commands.forEach(it -> Mission.requireNotPast(it.startDate(), today));
        Map<UUID, ProfileSummary> members = membersOf(familyId);
        commands.forEach(it -> requireConsentedMembers(members, it.participantProfileIds()));
        Instant now = time.now();
        Map<String, MissionVideo> checkedVideos = new LinkedHashMap<>();
        List<Mission> built = new ArrayList<>();
        for (CreateMissionCommand command : commands) {
            built.add(Mission.manual(
                    UUID.randomUUID(),
                    familyId,
                    command.title(),
                    command.targetMetric(),
                    command.targetValue(),
                    videoOf(command.videoId(), checkedVideos),
                    command.startDate(),
                    command.endDate(),
                    command.participantProfileIds(),
                    command.sessions(),
                    parent.profileId(),
                    now));
        }
        built.forEach(missions::save);
        built.forEach(it -> events.publishEvent(MissionEvents.created(it)));
        return MissionCreatedView.of(built);
    }

    /** 미션 영상. 카탈로그에 없으면 404. 여러 날 만들기는 같은 영상을 한 번만 확인한다. */
    private @Nullable MissionVideo videoOf(@Nullable String videoId, Map<String, MissionVideo> checked) {
        if (videoId == null) return null;
        MissionVideo known = checked.get(videoId);
        if (known != null) return known;
        if (videos.findById(videoId) == null) throw new VideoNotFoundException(videoId);
        MissionVideo video = new MissionVideo(videoId, null);
        checked.put(videoId, video);
        return video;
    }

    /**
     * 가족 미션 목록. `MINE` = 호출 계정의 이 가족 프로필이 참여자 · `FAMILY` = 참여자 2명 이상.
     * 차례: scope 로 먼저 거른다(진행도와 상관없다) → 남은 미션의 칸 끝 기록을 한 번에 읽는다 → 기간 안 미션의 미완료 참여자만
     * 다시 계산해 바뀐 것만 저장한다(기간이 끝난 미션 · 완료된 참여자는 저장된 값 그대로) → status 로 거른다(DONE 여부가 다시
     * 계산한 진행도에 달려 있다).
     */
    @Transactional
    public MissionListView list(UUID userId, UUID familyId, MissionScope scope, @Nullable MissionStatus status) {
        ProfileSummary caller = familyAccess.requireMember(userId, familyId);
        LocalDate today = time.today();
        Instant now = time.now();
        List<Mission> scoped = missions.findByFamily(familyId).stream()
                .filter(it -> switch (scope) {
                    case ALL -> true;
                    case MINE -> it.isParticipant(caller.profileId());
                    case FAMILY -> it.getParticipants().size() >= 2;
                })
                .toList();
        Map<UUID, MissionCompletions> done = completionsOf(scoped);
        List<Mission> filtered = scoped.stream()
                .map(it -> policy.refreshAll(it, doneOf(done, it), today, now))
                .filter(it -> status == null || it.statusOn(today) == status)
                .sorted(Comparator.comparing(Mission::getStartsOn).reversed().thenComparing(Mission::getCreatedAt))
                .toList();
        return new MissionListView(toViews(filtered, done, namesOf(familyId)));
    }

    /** 미션 한 건. 목록과 같은 모양 · 같은 권한(가족 구성원) · 같은 다시 계산 규칙이다. 없으면 404 `MISSION_NOT_FOUND`. */
    @Transactional
    public MissionView get(UUID userId, UUID missionId) {
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        familyAccess.requireMember(userId, mission.getFamilyId());
        Map<UUID, MissionCompletions> done = completionsOf(List.of(mission));
        Mission refreshed = policy.refreshAll(mission, doneOf(done, mission), time.today(), time.now());
        return toViews(List.of(refreshed), done, namesOf(refreshed.getFamilyId()))
                .getFirst();
    }

    /**
     * 보호자 확인(STEPS 등 사람이 말한 값). 걸음수가 목표 도달 전이면 422 `TARGET_NOT_REACHED`.
     * 서버가 재는 미션(타이머 · 영상 · 칸)은 확인할 것이 없어 바꾸지 않고 200 이다(FE 요청서 4장).
     */
    @Transactional
    public ConfirmParticipantView confirm(UUID userId, UUID missionId, UUID profileId) {
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        ProfileSummary parent = familyAccess.requireParent(userId, mission.getFamilyId());
        mission.participantOf(profileId);
        Instant now = time.now();
        Mission refreshed = policy.refreshParticipant(mission, profileId, now);
        MissionParticipant participant = refreshed.confirm(profileId, parent.profileId(), now);
        missions.save(refreshed);
        return new ConfirmParticipantView(
                refreshed.getId(),
                profileId,
                participant.isCompleted(),
                participant.getVerifiedBy(),
                participant.getConfirmedBy(),
                participant.getVerifiedAt());
    }

    private Map<UUID, ProfileSummary> membersOf(UUID familyId) {
        Map<UUID, ProfileSummary> members = new LinkedHashMap<>();
        profileQuery.summariesOfFamily(familyId).forEach(it -> members.put(it.profileId(), it));
        return members;
    }

    /** 참여자가 모두 이 가족이고, 동의가 필요한 사람은 동의가 살아 있어야 한다. */
    private static void requireConsentedMembers(Map<UUID, ProfileSummary> members, List<UUID> participantProfileIds) {
        for (UUID profileId : participantProfileIds) {
            if (!members.containsKey(profileId)) throw new NotFamilyMemberException(profileId);
        }
        for (UUID profileId : participantProfileIds) {
            ProfileSummary member = members.get(profileId);
            if (member.consentRequired() && !member.consentGiven()) throw new ParticipantConsentRequiredException();
        }
    }

    /** 미션들의 칸 끝 기록을 missionId IN 으로 한 번에 읽어 미션마다 나눈다. */
    private Map<UUID, MissionCompletions> completionsOf(List<Mission> ms) {
        Map<UUID, List<SessionCompletion>> byMission = new LinkedHashMap<>();
        completions.findByMissions(ms.stream().map(Mission::getId).toList()).forEach(it -> byMission
                .computeIfAbsent(it.missionId(), k -> new ArrayList<>())
                .add(it));
        Map<UUID, MissionCompletions> out = new LinkedHashMap<>();
        byMission.forEach((missionId, rows) -> out.put(missionId, MissionCompletions.of(rows)));
        return out;
    }

    private static MissionCompletions doneOf(Map<UUID, MissionCompletions> done, Mission mission) {
        return done.getOrDefault(mission.getId(), MissionCompletions.none());
    }

    private Map<UUID, String> namesOf(UUID familyId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        profileQuery.summariesOfFamily(familyId).forEach(it -> names.put(it.profileId(), it.name()));
        return names;
    }

    /** 목록 · 단건이 같이 쓰는 조립. 미션 영상은 한 번에 읽는다. 끝낸 칸은 사람마다 싣는다(doneSessions). */
    private List<MissionView> toViews(
            List<Mission> ordered, Map<UUID, MissionCompletions> done, Map<UUID, String> names) {
        Set<String> videoIds = new LinkedHashSet<>();
        for (Mission mission : ordered) {
            MissionVideo video = mission.getVideo();
            if (video != null) videoIds.add(video.videoId());
            for (MissionSession session : mission.getSessions()) {
                SessionClip clip = session.clip();
                if (clip != null) videoIds.add(clip.videoId());
            }
        }
        Map<String, ExerciseVideo> videoById = videoIds.isEmpty()
                ? Map.of()
                : videos.findAllByIds(videoIds).stream()
                        .collect(Collectors.toMap(ExerciseVideo::getVideoId, Function.identity(), (a, b) -> b));
        return ordered.stream()
                .map(m -> new MissionView(
                        m.getId(),
                        m.getTitle(),
                        m.getOrigin(),
                        m.getCoachRunId(),
                        m.getTargetMetric(),
                        m.getTargetValue(),
                        m.isServerVerifiable(),
                        m.getStartsOn(),
                        m.getEndsOn(),
                        m.getRationale(),
                        toVideoView(m.getVideo(), videoById),
                        m.getParticipants().stream()
                                .map(p -> new MissionParticipantView(
                                        p.getProfileId(),
                                        names.get(p.getProfileId()),
                                        p.getProgress(),
                                        p.isCompleted(),
                                        p.getVerifiedBy(),
                                        p.isNeedsGuardianCheck(),
                                        doneOf(done, m).positionsOf(p.getProfileId())))
                                .toList(),
                        m.getSessions().stream()
                                .map(it -> MissionSessionView.of(it, videoById))
                                .toList()))
                .toList();
    }

    private static @Nullable MissionVideoView toVideoView(
            @Nullable MissionVideo missionVideo, Map<String, ExerciseVideo> videoById) {
        if (missionVideo == null) return null;
        ExerciseVideo video = videoById.get(missionVideo.videoId());
        VideoMedia media = VideoMedia.of(video);
        return new MissionVideoView(
                missionVideo.videoId(),
                video == null ? null : video.getTitle(),
                video == null ? ExerciseVideo.youtubeUrl(missionVideo.videoId()) : video.getUrl(),
                video == null ? null : video.getDurationSec(),
                missionVideo.startSec(),
                media.mediaUrl(),
                media.thumbnailUrl());
    }
}
