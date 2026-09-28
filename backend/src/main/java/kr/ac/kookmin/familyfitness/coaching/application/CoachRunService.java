package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
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
import kr.ac.kookmin.familyfitness.coaching.domain.CoachApprover;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunAlreadyDecidedException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunInProgressException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidRunDateException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.NoMeasuredMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
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
 * 코치 실행 유스케이스: 시작(202, 보호자) · 조회 · 가장 최근 조회 · 승인 · 거절.
 * 한 번의 편성은 한 사람의 하루다(FE 요청서 1장 ②). 미션은 승인 트랜잭션 안에서만 만들어진다(직접 만들기 제외).
 * 편성은 보호자가 누를 때만 시작한다 — 일요일 20시 자동 편성은 없앴다(결정 3).
 */
@Service
public class CoachRunService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoachRunRepository runs;
    private final MissionRepository missions;
    private final ExerciseVideoRepository videos;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final FitnessQuery fitnessQuery;
    private final ApplicationEventPublisher events;
    private final AppTime time;
    private final CoachRunTimeLimit timeLimit;

    public CoachRunService(
            CoachRunRepository runs,
            MissionRepository missions,
            ExerciseVideoRepository videos,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            FitnessQuery fitnessQuery,
            ApplicationEventPublisher events,
            AppTime time,
            CoachRunTimeLimit timeLimit) {
        this.runs = runs;
        this.missions = missions;
        this.videos = videos;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.fitnessQuery = fitnessQuery;
        this.events = events;
        this.time = time;
        this.timeLimit = timeLimit;
    }

    /**
     * RUNNING 으로 저장하고 이벤트만 발행한다. AI 호출은 커밋 후 비동기. 판단 차례:
     * 1) 보호자만(403 NOT_A_PARENT) 2) 지난 날짜면 422 INVALID_DATE 3) 대상이 이 가족 구성원이 아니면 422 NOT_FAMILY_MEMBER
     * 4) 대상의 보호자 동의가 없으면 422 CONSENT_REQUIRED 5) 대상이 측정 대상(만 4세 이상)인데 측정 기록이 없으면 422 NO_MEASURED_MEMBER
     * 6) 같은 (대상, 날짜)의 RUNNING 이 있으면 409 RUN_IN_PROGRESS(결정 1).
     * 잠금은 coach_runs.lock_key 유니크 인덱스라 동시에 들어온 두 요청도 하나만 통과한다. {@link CoachRunTimeLimit} 보다 오래된
     * RUNNING 은 서버가 끝내지 못한 실행이라 FAILED 로 바꾸고 잠금을 푼다.
     * 새 실행이 들어가면 같은 (대상, 날짜)의 AWAITING_APPROVAL 은 고정 사유로 REJECTED 가 된다. APPROVED 는 막지 않는다.
     */
    @Transactional
    public CoachRunAcceptedView start(UUID userId, UUID familyId, StartCoachRunCommand command) {
        ProfileSummary caller = familyAccess.requireParent(userId, familyId);
        UUID subjectId = command.profileId();
        LocalDate date = command.date();
        LocalDate today = time.today();
        if (date.isBefore(today)) throw new InvalidRunDateException(date, today);

        ProfileSummary subject = profileQuery.findSummary(subjectId);
        if (subject == null || !subject.familyId().equals(familyId)) throw new NotFamilyMemberException(subjectId);
        ParticipantConsent.require(subject);
        if (subject.measurable() && !fitnessQuery.hasAnyTest(List.of(subjectId))) {
            throw new NoMeasuredMemberException(subjectId);
        }

        var now = time.now();
        String lockKey = CoachRun.lockKeyOf(subjectId, date);
        runs.failStaleLock(lockKey, timeLimit.staleBefore(now), timeLimit.staleReason(), now);
        if (runs.isLocked(lockKey)) throw new CoachRunInProgressException(subjectId, date);

        CoachRun run = CoachRun.start(
                UUID.randomUUID(), familyId, subjectId, date, command.conditions(), caller.profileId(), now);
        if (!runs.insertRunning(run)) throw new CoachRunInProgressException(subjectId, date);
        int superseded = runs.rejectAwaitingOf(subjectId, date, CoachRun.SUPERSEDED_REASON, now);
        if (superseded > 0) log.info("새 편성으로 기다리던 제안 {}건을 거절했다: run={}", superseded, run.getId());
        events.publishEvent(new CoachRunRequested(run.getId()));
        return new CoachRunAcceptedView(run.getId(), run.getStatus(), CoachRun.POLL_AFTER_MS);
    }

    @Transactional(readOnly = true)
    public CoachRunView get(UUID userId, UUID runId) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        ProfileSummary caller = familyAccess.requireMember(userId, run.getFamilyId());
        return toView(run, run.isAwaitingApproval() && caller.isParent());
    }

    /**
     * 가장 최근 실행(상태와 상관없이). profileId 가 있으면 그 프로필을 대상으로 짠 것, 없으면 가족 전체에서.
     * 아이마다 제안을 따로 찾게 하려는 것이다 — 가족 하나로만 주면 형제의 제안이 서로를 가린다. 없으면 404 COACH_RUN_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public CoachRunView latest(UUID userId, UUID familyId, @Nullable UUID profileId) {
        ProfileSummary caller = familyAccess.requireMember(userId, familyId);
        CoachRun run =
                profileId == null ? runs.findLatestOfFamily(familyId) : runs.findLatestOfSubject(familyId, profileId);
        if (run == null) throw CoachRunNotFoundException.latestOf(familyId, profileId);
        return toView(run, run.isAwaitingApproval() && caller.isParent());
    }

    /**
     * 한 트랜잭션: 도메인 승인 → 동의 확인 → 조건부 UPDATE(0행이면 409) → 제안 복사로 미션·참여자 INSERT.
     * 승인자는 요청 값이 아니라 {@link FamilyAccess#requireParent} 가 돌려준 부모 프로필이다.
     * 복사할 참여자 중 보호자 동의가 없거나 거둔 사람이 있으면 422 CONSENT_REQUIRED 이고 아무것도 바뀌지 않는다.
     */
    @Transactional
    public ApproveCoachRunView approve(UUID userId, UUID runId) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        ProfileSummary parent = familyAccess.requireParent(userId, run.getFamilyId());
        var now = time.now();
        run.approve(new CoachApprover(parent.profileId(), parent.familyId(), parent.isParent()), now);
        requireParticipantConsent(run);
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

    /** 미션으로 복사될 참여자의 동의만 본다. 가족에서 빠진 프로필은 판정하지 않는다. */
    private void requireParticipantConsent(CoachRun run) {
        Map<UUID, ProfileSummary> members = profileQuery.summariesOfFamily(run.getFamilyId()).stream()
                .collect(Collectors.toMap(ProfileSummary::profileId, Function.identity(), (a, b) -> a));
        boolean missing = run.getProposals().stream()
                .flatMap(item -> item.participants().stream())
                .map(participant -> members.get(participant.profileId()))
                .anyMatch(member -> member != null && ParticipantConsent.missing(member));
        if (missing) throw new ParticipantConsentRequiredException();
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
                                .toList(),
                        item.sessions().stream().map(MissionSessionView::of).toList()))
                .toList();
        return new CoachRunView(
                run.getId(),
                run.getFamilyId(),
                run.getStatus(),
                run.getWeekStart(),
                run.getSubjectProfileId(),
                run.getRunDate(),
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
