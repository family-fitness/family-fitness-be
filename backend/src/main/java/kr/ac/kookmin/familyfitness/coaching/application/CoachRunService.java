package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.AlreadyRunThisWeekException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachApprover;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunAlreadyDecidedException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunInProgressException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.NoMeasuredMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코치 실행 유스케이스: 시작(202) · 조회 · 승인 · 거절.
 * 미션은 승인 트랜잭션 안에서만 만들어진다(직접 만들기 제외).
 */
@Service
public class CoachRunService {
    public static final Set<CoachRunStatus> DECIDED_OR_AWAITING =
            Set.of(CoachRunStatus.AWAITING_APPROVAL, CoachRunStatus.APPROVED);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoachRunRepository runs;
    private final MissionRepository missions;
    private final ExerciseVideoRepository videos;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final FitnessQuery fitnessQuery;
    private final ApplicationEventPublisher events;
    private final AppTime time;

    public CoachRunService(
            CoachRunRepository runs,
            MissionRepository missions,
            ExerciseVideoRepository videos,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            FitnessQuery fitnessQuery,
            ApplicationEventPublisher events,
            AppTime time) {
        this.runs = runs;
        this.missions = missions;
        this.videos = videos;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.fitnessQuery = fitnessQuery;
        this.events = events;
        this.time = time;
    }

    /**
     * RUNNING 으로 저장하고 이벤트만 발행한다. AI 호출은 커밋 후 비동기.
     * ▲ 같은 가족의 동시 요청은 존재 검사 사이의 경합이 가능하다(부분 유니크 인덱스를 못 쓰는 H2 호환 스키마).
     */
    @Transactional
    public CoachRunAcceptedView start(UUID userId, UUID familyId, StartCoachRunCommand command) {
        ProfileSummary caller = familyAccess.requireMember(userId, familyId);
        var weekStart = command.weekStart() == null ? time.thisWeekStart() : AppTime.weekStartOf(command.weekStart());

        if (runs.existsByFamilyAndStatus(familyId, CoachRunStatus.RUNNING)) {
            throw new CoachRunInProgressException(familyId);
        }
        if (runs.existsByFamilyAndWeekAndStatusIn(familyId, weekStart, DECIDED_OR_AWAITING)) {
            throw new AlreadyRunThisWeekException(weekStart);
        }
        List<UUID> memberIds = profileQuery.summariesOfFamily(familyId).stream()
                .map(ProfileSummary::profileId)
                .toList();
        if (!fitnessQuery.hasAnyTest(memberIds)) throw new NoMeasuredMemberException(familyId);

        CoachRun run = CoachRun.start(
                UUID.randomUUID(),
                familyId,
                weekStart,
                TriggerType.MANUAL,
                command.daysPerWeek(),
                command.minutesPerSession(),
                caller.profileId(),
                time.now());
        runs.save(run);
        events.publishEvent(new CoachRunRequested(run.getId()));
        return new CoachRunAcceptedView(run.getId(), run.getStatus(), CoachRun.POLL_AFTER_MS);
    }

    /**
     * 일요일 20:00 스케줄(보드 F3 `trigger = SCHEDULE`). 사람이 아무것도 안 해도 주 1회 제안이 만들어진다.
     * 실행 중·이번 주 결정된 run 이 있거나 측정된 구성원이 없으면 조용히 건너뛴다(예외가 아니다).
     */
    @Transactional
    public @Nullable UUID startScheduled(UUID familyId) {
        var weekStart = time.thisWeekStart();
        if (runs.existsByFamilyAndStatus(familyId, CoachRunStatus.RUNNING)) return null;
        if (runs.existsByFamilyAndWeekAndStatusIn(familyId, weekStart, DECIDED_OR_AWAITING)) return null;
        List<UUID> memberIds = profileQuery.summariesOfFamily(familyId).stream()
                .map(ProfileSummary::profileId)
                .toList();
        if (memberIds.isEmpty() || !fitnessQuery.hasAnyTest(memberIds)) return null;

        CoachRun run = CoachRun.start(
                UUID.randomUUID(),
                familyId,
                weekStart,
                TriggerType.SCHEDULE,
                StartCoachRunCommand.DEFAULT_DAYS_PER_WEEK,
                StartCoachRunCommand.DEFAULT_MINUTES_PER_SESSION,
                null,
                time.now());
        runs.save(run);
        events.publishEvent(new CoachRunRequested(run.getId()));
        return run.getId();
    }

    @Transactional(readOnly = true)
    public CoachRunView get(UUID userId, UUID runId) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        ProfileSummary caller = familyAccess.requireMember(userId, run.getFamilyId());
        return toView(run, run.isAwaitingApproval() && caller.isParent());
    }

    /**
     * 한 트랜잭션: 도메인 승인 → 조건부 UPDATE(0행이면 409) → 제안 복사로 미션·참여자 INSERT.
     * 승인자는 요청 값이 아니라 {@link FamilyAccess#requireParent} 가 돌려준 부모 프로필이다.
     */
    @Transactional
    public ApproveCoachRunView approve(UUID userId, UUID runId) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        ProfileSummary parent = familyAccess.requireParent(userId, run.getFamilyId());
        var now = time.now();
        run.approve(new CoachApprover(parent.profileId(), parent.familyId(), parent.isParent()), now);
        if (!runs.approveIfAwaiting(run)) {
            CoachRunStatus current = runs.currentStatus(runId);
            throw new CoachRunAlreadyDecidedException(current == null ? run.getStatus() : current);
        }

        List<CreatedMissionView> created = new ArrayList<>();
        for (CoachProposalItem item : run.proposalsForMissionCreation()) {
            if (item.participants().isEmpty()) {
                log.warn("참여자 없는 제안 항목은 미션으로 만들지 않는다: run={} position={}", run.getId(), item.position());
                continue;
            }
            Mission mission =
                    missions.save(Mission.fromProposal(UUID.randomUUID(), run, item, parent.profileId(), now));
            created.add(new CreatedMissionView(mission.getId(), mission.getTitle(), mission.getOrigin()));
        }
        return new ApproveCoachRunView(
                run.getId(),
                run.getStatus(),
                Objects.requireNonNull(run.getApprovedBy()),
                Objects.requireNonNull(run.getApprovedAt()),
                List.copyOf(created));
    }

    @Transactional
    public RejectCoachRunView reject(UUID userId, UUID runId, @Nullable String reason) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        ProfileSummary parent = familyAccess.requireParent(userId, run.getFamilyId());
        run.reject(new CoachApprover(parent.profileId(), parent.familyId(), parent.isParent()), reason, time.now());
        if (!runs.rejectIfAwaiting(run)) {
            CoachRunStatus current = runs.currentStatus(runId);
            throw new CoachRunAlreadyDecidedException(current == null ? run.getStatus() : current);
        }
        return new RejectCoachRunView(run.getId(), run.getStatus(), run.getRejectedReason(), 0);
    }

    private CoachRunView toView(CoachRun run, boolean canApprove) {
        Set<String> videoIds = new LinkedHashSet<>();
        for (CoachProposalItem item : run.getProposals()) {
            ProposalVideo video = item.video();
            if (video != null) videoIds.add(video.videoId());
        }
        Map<String, ExerciseVideo> videoById = videoIds.isEmpty()
                ? Map.of()
                : videos.findAllByIds(videoIds).stream()
                        .collect(Collectors.toMap(ExerciseVideo::getVideoId, Function.identity(), (a, b) -> b));
        List<ProposalView> proposals = run.getProposals().stream()
                .map(item -> new ProposalView(
                        item.position(),
                        item.title(),
                        item.rationale(),
                        item.targetMetric(),
                        item.targetValue(),
                        item.startsOn() == null ? run.getWeekStart() : item.startsOn(),
                        item.endsOn() == null ? run.getWeekEnd() : item.endsOn(),
                        item.participants().stream()
                                .map(it -> new ProposalParticipantView(it.profileId(), it.role(), it.coachRole()))
                                .toList(),
                        toVideoView(item.video(), videoById),
                        item.citations().stream()
                                .map(it -> new ProposalCitationView(it.index(), it.label(), it.chunkId(), it.url()))
                                .toList()))
                .toList();
        return new CoachRunView(
                run.getId(),
                run.getFamilyId(),
                run.getStatus(),
                run.getWeekStart(),
                run.getSummary(),
                run.getSteps(),
                proposals.isEmpty() ? null : proposals,
                canApprove,
                missions.countByCoachRun(run.getId()),
                run.getRejectedReason());
    }

    private static @Nullable ProposalVideoView toVideoView(
            @Nullable ProposalVideo proposalVideo, Map<String, ExerciseVideo> videoById) {
        if (proposalVideo == null) return null;
        ExerciseVideo video = videoById.get(proposalVideo.videoId());
        return new ProposalVideoView(
                proposalVideo.videoId(),
                video == null ? null : video.getTitle(),
                video == null ? "https://www.youtube.com/watch?v=" + proposalVideo.videoId() : video.getUrl(),
                proposalVideo.startSec(),
                video == null ? List.of() : video.getBadges());
    }
}
