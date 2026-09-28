package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.time.LocalDate;
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
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 미션 직접 만들기 · 목록 · 단건 · 보호자 확인. */
@Service
public class MissionService {
    private final MissionRepository missions;
    private final ExerciseVideoRepository videos;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final MissionCompletionPolicy policy;
    private final AppTime time;

    public MissionService(
            MissionRepository missions,
            ExerciseVideoRepository videos,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            MissionCompletionPolicy policy,
            AppTime time) {
        this.missions = missions;
        this.videos = videos;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.policy = policy;
        this.time = time;
    }

    /**
     * 보호자가 직접 만든다. 참여자는 이 가족 구성원이어야 하고(422 `NOT_FAMILY_MEMBER`),
     * 동의가 필요한데 없는 프로필이 끼면 422 `CONSENT_REQUIRED` 다.
     * 칸의 영상 id 는 카탈로그로 확인하지 않는다 — 칸은 영상 구간의 사본을 든다.
     */
    @Transactional
    public MissionCreatedView create(UUID userId, UUID familyId, CreateMissionCommand command) {
        ProfileSummary parent = familyAccess.requireParent(userId, familyId);
        requireConsentedMembers(familyId, command.participantProfileIds());
        MissionVideo video = null;
        String videoId = command.videoId();
        if (videoId != null) {
            if (videos.findById(videoId) == null) throw new VideoNotFoundException(videoId);
            video = new MissionVideo(videoId, null);
        }
        Mission mission = missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                command.title(),
                command.targetMetric(),
                command.targetValue(),
                video,
                command.startDate(),
                command.endDate(),
                command.participantProfileIds(),
                command.sessions(),
                parent.profileId(),
                time.now()));
        return new MissionCreatedView(
                mission.getId(), mission.getOrigin(), mission.getCoachRunId(), mission.isServerVerifiable());
    }

    /**
     * 가족 미션 목록. `MINE` = 호출 계정의 이 가족 프로필이 참여자 · `FAMILY` = 참여자 2명 이상.
     * 차례: scope 로 먼저 거른다(진행도와 상관없다) → 남은 것 중 기간 안 미션의 미완료 참여자만 다시 계산해 바뀐 것만 저장한다
     * (기간이 끝난 미션 · 완료된 참여자는 저장된 값 그대로) → status 로 거른다(DONE 여부가 다시 계산한 진행도에 달려 있다).
     */
    @Transactional
    public MissionListView list(UUID userId, UUID familyId, MissionScope scope, @Nullable MissionStatus status) {
        ProfileSummary caller = familyAccess.requireMember(userId, familyId);
        LocalDate today = time.today();
        Instant now = time.now();
        List<Mission> filtered = missions.findByFamily(familyId).stream()
                .filter(it -> switch (scope) {
                    case ALL -> true;
                    case MINE -> it.isParticipant(caller.profileId());
                    case FAMILY -> it.getParticipants().size() >= 2;
                })
                .map(it -> policy.refreshAll(it, today, now))
                .filter(it -> status == null || it.statusOn(today) == status)
                .sorted(Comparator.comparing(Mission::getStartsOn).reversed().thenComparing(Mission::getCreatedAt))
                .toList();
        return new MissionListView(toViews(filtered, namesOf(familyId)));
    }

    /** 미션 한 건. 목록과 같은 모양 · 같은 권한(가족 구성원) · 같은 다시 계산 규칙이다. 없으면 404 `MISSION_NOT_FOUND`. */
    @Transactional
    public MissionView get(UUID userId, UUID missionId) {
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        familyAccess.requireMember(userId, mission.getFamilyId());
        Mission refreshed = policy.refreshAll(mission, time.today(), time.now());
        return toViews(List.of(refreshed), namesOf(refreshed.getFamilyId())).getFirst();
    }

    /** 보호자 확인(STEPS 등 사람이 말한 값). 목표 도달 전이면 422 `TARGET_NOT_REACHED`. */
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

    /** 참여자가 모두 이 가족이고, 동의가 필요한 사람은 동의가 살아 있어야 한다. */
    private void requireConsentedMembers(UUID familyId, List<UUID> participantProfileIds) {
        Map<UUID, ProfileSummary> members = new LinkedHashMap<>();
        profileQuery.summariesOfFamily(familyId).forEach(it -> members.put(it.profileId(), it));
        for (UUID profileId : participantProfileIds) {
            if (!members.containsKey(profileId)) throw new NotFamilyMemberException(profileId);
        }
        for (UUID profileId : participantProfileIds) {
            ProfileSummary member = members.get(profileId);
            if (member.consentRequired() && !member.consentGiven()) throw new ParticipantConsentRequiredException();
        }
    }

    private Map<UUID, String> namesOf(UUID familyId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        profileQuery.summariesOfFamily(familyId).forEach(it -> names.put(it.profileId(), it.name()));
        return names;
    }

    /** 목록 · 단건이 같이 쓰는 조립. 미션 영상은 한 번에 읽는다. */
    private List<MissionView> toViews(List<Mission> ordered, Map<UUID, String> names) {
        Set<String> videoIds = new LinkedHashSet<>();
        for (Mission mission : ordered) {
            MissionVideo video = mission.getVideo();
            if (video != null) videoIds.add(video.videoId());
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
                                        p.isNeedsGuardianCheck()))
                                .toList(),
                        m.getSessions().stream()
                                .map(MissionService::toSessionView)
                                .toList()))
                .toList();
    }

    private static MissionSessionView toSessionView(MissionSession session) {
        SessionClip clip = session.clip();
        return new MissionSessionView(
                session.position(),
                session.phase(),
                session.title(),
                session.factor(),
                session.minutes(),
                clip == null
                        ? null
                        : new SessionClipView(clip.videoId(), clip.startSec(), clip.endSec(), clip.title()));
    }

    private static @Nullable MissionVideoView toVideoView(
            @Nullable MissionVideo missionVideo, Map<String, ExerciseVideo> videoById) {
        if (missionVideo == null) return null;
        ExerciseVideo video = videoById.get(missionVideo.videoId());
        return new MissionVideoView(
                missionVideo.videoId(),
                video == null ? null : video.getTitle(),
                video == null ? "https://www.youtube.com/watch?v=" + missionVideo.videoId() : video.getUrl(),
                video == null ? null : video.getDurationSec(),
                missionVideo.startSec());
    }
}
