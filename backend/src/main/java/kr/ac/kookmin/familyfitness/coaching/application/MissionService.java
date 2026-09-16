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
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 미션 직접 만들기 · 목록 · 보호자 확인. */
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

    @Transactional
    public MissionCreatedView create(UUID userId, UUID familyId, CreateMissionCommand command) {
        ProfileSummary parent = familyAccess.requireParent(userId, familyId);
        Set<UUID> memberIds = profileQuery.summariesOfFamily(familyId).stream()
                .map(ProfileSummary::profileId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (UUID profileId : command.participantProfileIds()) {
            if (!memberIds.contains(profileId)) throw new NotFamilyMemberException(profileId);
        }
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
                parent.profileId(),
                time.now()));
        return new MissionCreatedView(
                mission.getId(), mission.getOrigin(), mission.getCoachRunId(), mission.isServerVerifiable());
    }

    /**
     * 가족 미션 목록. 읽을 때 미완료 참여자의 진행도를 다시 계산해 바뀐 것만 저장한다(write-through).
     * `MINE` = 호출 계정의 이 가족 프로필이 참여자 · `FAMILY` = 참여자 2명 이상.
     */
    @Transactional
    public MissionListView list(UUID userId, UUID familyId, MissionScope scope, @Nullable MissionStatus status) {
        ProfileSummary caller = familyAccess.requireMember(userId, familyId);
        LocalDate today = time.today();
        Instant now = time.now();
        Map<UUID, String> names = new LinkedHashMap<>();
        profileQuery.summariesOfFamily(familyId).forEach(it -> names.put(it.profileId(), it.name()));
        List<Mission> all = missions.findByFamily(familyId).stream()
                .map(it -> policy.refreshAll(it, now))
                .toList();
        List<Mission> filtered = all.stream()
                .filter(it -> switch (scope) {
                    case ALL -> true;
                    case MINE -> it.isParticipant(caller.profileId());
                    case FAMILY -> it.getParticipants().size() >= 2;
                })
                .filter(it -> status == null || it.statusOn(today) == status)
                .toList();
        Set<String> videoIds = new LinkedHashSet<>();
        for (Mission mission : filtered) {
            MissionVideo video = mission.getVideo();
            if (video != null) videoIds.add(video.videoId());
        }
        Map<String, ExerciseVideo> videoById = videoIds.isEmpty()
                ? Map.of()
                : videos.findAllByIds(videoIds).stream()
                        .collect(Collectors.toMap(ExerciseVideo::getVideoId, Function.identity(), (a, b) -> b));
        List<MissionView> views = filtered.stream()
                .sorted(Comparator.comparing(Mission::getStartsOn).reversed().thenComparing(Mission::getCreatedAt))
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
                                .toList()))
                .toList();
        return new MissionListView(views);
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
