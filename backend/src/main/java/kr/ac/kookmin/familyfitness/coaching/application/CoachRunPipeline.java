package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 파이프라인의 트랜잭션 단계. 폴링(최대 60초) 동안 트랜잭션을 잡지 않도록 준비·마무리를 따로 자른다.
 * 비동기 리스너가 AFTER_COMMIT 콜백 안에서 부르므로 항상 REQUIRES_NEW.
 */
@Component
public class CoachRunPipeline {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoachRunRepository runs;
    private final ProfileQuery profileQuery;
    private final FitnessQuery fitnessQuery;
    private final ExerciseVideoRepository videos;
    private final JsonMapper jsonMapper;
    private final AppTime time;
    private final LabelBasedProposalPlanner fallbackPlanner;

    public CoachRunPipeline(
            CoachRunRepository runs,
            ProfileQuery profileQuery,
            FitnessQuery fitnessQuery,
            ExerciseVideoRepository videos,
            JsonMapper jsonMapper,
            AppTime time,
            LabelBasedProposalPlanner fallbackPlanner) {
        this.runs = runs;
        this.profileQuery = profileQuery;
        this.fitnessQuery = fitnessQuery;
        this.videos = videos;
        this.jsonMapper = jsonMapper;
        this.time = time;
        this.fallbackPlanner = fallbackPlanner;
    }

    /** AI 장애 시 라벨 기반 대체 편성. 실행 파라미터는 run 에 저장된 값을 쓴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public @Nullable CoachRunResult planFallback(UUID runId, String reason) {
        CoachRun run = load(runId);
        return fallbackPlanner.plan(
                run.getFamilyId(),
                run.getWeekStart(),
                run.getDaysPerWeek(),
                run.getMinutesPerSession(),
                time.today(),
                reason);
    }

    /** 가족 프로필과 최신 측정으로 AI 요청을 만든다. AI 로 이름·생년월일은 나가지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CoachRunRequest prepare(UUID runId) {
        CoachRun run = load(runId);
        var today = time.today();
        List<CoachRunRequest.Participant> participants = profileQuery.detailsOfFamily(run.getFamilyId()).stream()
                .map(details -> {
                    LatestFitness latest = fitnessQuery.latestOf(details.profileId());
                    AiProfile profile = AiProfileFactory.of(
                            details,
                            latest == null ? Map.of() : latest.measurements(),
                            latest == null ? null : latest.heightCm(),
                            latest == null ? null : latest.weightKg(),
                            today);
                    return AiProfileFactory.participant(details, profile);
                })
                .toList();
        return new CoachRunRequest(
                participants, run.getWeekStart().toString(), 1, run.getDaysPerWeek(), run.getMinutesPerSession());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachAiRun(UUID runId, String aiRunId) {
        CoachRun run = load(runId);
        run.attachAiRun(aiRunId, time.now());
        runs.save(run);
    }

    /** `succeeded` 결과를 제안 항목으로 바꿔 붙이고 AWAITING_APPROVAL 로 옮긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID runId, CoachRunResult result) {
        CoachRun run = load(runId);
        CoachRunResult.Proposal proposal = result.proposal();
        if (proposal == null) {
            throw new IllegalStateException("succeeded 인데 proposal 이 없다: " + result.runId());
        }
        List<ProfileDetails> members = profileQuery.detailsOfFamily(run.getFamilyId());
        Map<UUID, ProfileRole> roles = new LinkedHashMap<>();
        Map<UUID, String> coachRoles = new LinkedHashMap<>();
        for (ProfileDetails member : members) {
            roles.put(member.profileId(), member.role());
            coachRoles.put(member.profileId(), CoachRoles.of(member.role(), member.supportMode()));
        }
        Set<String> videoIds = new LinkedHashSet<>();
        for (CoachRunResult.Mission mission : proposal.missions()) {
            for (CoachRunResult.Session session : mission.sessions()) {
                CoachRunResult.Video video = session.video();
                if (video != null) videoIds.add(video.videoId());
            }
        }
        Set<String> knownVideoIds = videos.findAllByIds(videoIds).stream()
                .map(ExerciseVideo::getVideoId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ProposalConverter converter = new ProposalConverter(
                ProfileRef.indexOf(
                        members.stream().map(ProfileDetails::profileId).toList()),
                roles,
                coachRoles,
                knownVideoIds);
        run.complete(
                ProposalConverter.steps(result),
                converter.convert(proposal),
                jsonMapper.writeValueAsString(proposal),
                ProposalConverter.summary(result),
                null,
                time.now());
        runs.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            UUID runId,
            String reason,
            @Nullable List<CoachStep> steps,
            boolean refused,
            @Nullable String refusalReason) {
        CoachRun run = load(runId);
        if (run.getStatus() != CoachRunStatus.RUNNING) {
            log.warn("이미 끝난 실행의 실패 기록은 무시한다: run={} status={}", runId, run.getStatus());
            return;
        }
        run.fail(reason, time.now(), steps == null ? run.getSteps() : steps, refused, refusalReason);
        runs.save(run);
    }

    private CoachRun load(UUID runId) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        return run;
    }
}
