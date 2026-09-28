package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
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
    private final JsonMapper jsonMapper;
    private final AppTime time;
    private final LabelBasedProposalPlanner fallbackPlanner;
    private final ExerciseClipRepository clips;
    private final ExerciseVideoRepository videos;

    public CoachRunPipeline(
            CoachRunRepository runs,
            ProfileQuery profileQuery,
            FitnessQuery fitnessQuery,
            JsonMapper jsonMapper,
            AppTime time,
            LabelBasedProposalPlanner fallbackPlanner,
            ExerciseClipRepository clips,
            ExerciseVideoRepository videos) {
        this.runs = runs;
        this.profileQuery = profileQuery;
        this.fitnessQuery = fitnessQuery;
        this.jsonMapper = jsonMapper;
        this.time = time;
        this.fallbackPlanner = fallbackPlanner;
        this.clips = clips;
        this.videos = videos;
    }

    /** AI 장애 시 라벨 기반 대체 편성. 대상 · 날짜 · 조건은 run 에 저장된 값을 쓴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public @Nullable CoachRunResult planFallback(UUID runId, String reason) {
        CoachRun run = load(runId);
        return fallbackPlanner.plan(subjectOf(run), requireRunDate(run), requireConditions(run), time.today(), reason);
    }

    /**
     * 대상 한 명(주행자)의 하루를 AI 에 요청한다(결정 2). 다른 구성원은 보내지 않는다 — 응원으로 보내면 AI 가 일간 참여자로 붙이고
     * (ai:coach/compose.py 일간 참여자 = 주행자 + 응원), 여럿을 보내면 AI 잠금(ref 가 하나라도 겹치면 409)에 걸린다.
     * AI 로 이름 · 생년월일은 나가지 않는다. 대상의 보호자 동의가 그 사이 거둬졌으면 예외 → FAILED.
     * constraints: 하루 한 번(days_per_week 1) · minutes · 주간 미션 없음(weekly_minutes null) · quiet ·
     * small_space(HOME 이면 true — AI 는 home_ok 클립만 남긴다, ai:video/catalog.py _fits) ·
     * no_props true(FE 목도 도구 없는 클립만 쓴다) · focus_factor · with_companion(둘은 AI 계약에 아직 없어 AI 가 무시한다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CoachRunRequest prepare(UUID runId) {
        CoachRun run = load(runId);
        ProfileDetails subject = subjectOf(run);
        CoachRunConditions conditions = requireConditions(run);
        LatestFitness latest = fitnessQuery.latestOf(subject.profileId());
        AiProfile profile = AiProfileFactory.of(
                subject,
                latest == null ? Map.of() : latest.measurements(),
                latest == null ? null : latest.heightCm(),
                latest == null ? null : latest.weightKg(),
                time.today());
        return new CoachRunRequest(
                List.of(new CoachRunRequest.Participant(profile, CoachRoles.DRIVER)),
                requireRunDate(run).toString(),
                1,
                new CoachRunRequest.Constraints(
                        1,
                        conditions.minutes(),
                        null,
                        conditions.quiet(),
                        conditions.place() == CoachPlace.HOME,
                        true,
                        conditions.focusFactor() == null
                                ? null
                                : conditions.focusFactor().getLabel(),
                        conditions.withParent()));
    }

    /** AI 접수 번호를 남긴다. 그 사이 정리 작업이 끝낸 실행이면 false — 호출자는 폴링하지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean attachAiRun(UUID runId, String aiRunId) {
        CoachRun run = load(runId);
        if (alreadyFinished(run, "AI 접수 기록")) return false;
        run.attachAiRun(aiRunId, time.now());
        return written(runs.attachAiRunIfRunning(run), runId, "AI 접수 기록");
    }

    /** `succeeded` 결과를 제안 항목(칸 포함)으로 바꿔 붙이고 AWAITING_APPROVAL 로 옮긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID runId, CoachRunResult result) {
        CoachRun run = load(runId);
        if (alreadyFinished(run, "제안")) return;
        CoachRunResult.Proposal proposal = result.proposal();
        if (proposal == null) {
            throw new IllegalStateException("succeeded 인데 proposal 이 없다: " + result.runId());
        }
        ProfileDetails subject = subjectOf(run);
        @Nullable UUID companion = requireConditions(run).withParent() ? run.getRequestedBy() : null;
        ProposalConverter converter =
                new ProposalConverter(subject.profileId(), subject.role(), companion, clipTitlesOf(proposal));
        run.complete(
                ProposalConverter.steps(result),
                converter.convert(proposal),
                jsonMapper.writeValueAsString(proposal),
                ProposalConverter.summary(result),
                null,
                time.now());
        written(runs.finishIfRunning(run), runId, "제안");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            UUID runId,
            String reason,
            @Nullable List<CoachStep> steps,
            boolean refused,
            @Nullable String refusalReason) {
        CoachRun run = load(runId);
        if (alreadyFinished(run, "실패 기록")) return;
        run.fail(reason, time.now(), steps == null ? run.getSteps() : steps, refused, refusalReason);
        written(runs.finishIfRunning(run), runId, "실패 기록");
    }

    /** 제안의 칸 영상 구간 제목을 클립 표 한 번 · 영상 표 한 번의 조회로 모은다(칸마다 조회하지 않는다). */
    private ClipTitles clipTitlesOf(CoachRunResult.Proposal proposal) {
        Set<String> clipIds = new LinkedHashSet<>();
        Set<String> videoIds = new LinkedHashSet<>();
        for (CoachRunResult.Mission mission : proposal.missions()) {
            for (CoachRunResult.Session session : mission.sessions()) {
                CoachRunResult.Video video = session.video();
                if (video == null || video.videoId().isBlank()) continue;
                videoIds.add(video.videoId());
                clipIds.add(ExerciseClip.idOf(video.videoId(), ProposalConverter.clipStart(video)));
            }
        }
        if (videoIds.isEmpty()) return ClipTitles.none();
        Map<String, String> clipTitles = clips.findAllByIds(clipIds).stream()
                .collect(Collectors.toMap(ExerciseClip::clipId, ExerciseClip::title, (a, b) -> a));
        Map<String, String> videoTitles = videos.findAllByIds(videoIds).stream()
                .collect(Collectors.toMap(ExerciseVideo::getVideoId, ExerciseVideo::getTitle, (a, b) -> a));
        return ClipTitles.of(clipTitles, videoTitles);
    }

    /** 읽었을 때 이미 RUNNING 이 아니다(정리 작업 · 새 요청이 FAILED 로 바꿨다). 결과를 버린다. */
    private boolean alreadyFinished(CoachRun run, String what) {
        if (run.getStatus() == CoachRunStatus.RUNNING) return false;
        log.warn("이미 끝난 실행이라 {} 저장을 건너뛴다: run={} status={}", what, run.getId(), run.getStatus());
        return true;
    }

    /** 조건부 UPDATE 가 0행이면 읽은 뒤 커밋 전에 다른 트랜잭션이 끝낸 것이다. 그쪽 상태를 그대로 둔다. */
    private boolean written(boolean updated, UUID runId, String what) {
        if (!updated) {
            log.warn("읽은 뒤 다른 트랜잭션이 끝낸 실행이라 {} 저장을 건너뛴다: run={} status={}", what, runId, runs.currentStatus(runId));
        }
        return updated;
    }

    private CoachRun load(UUID runId) {
        CoachRun run = runs.findById(runId);
        if (run == null) throw new CoachRunNotFoundException(runId);
        return run;
    }

    /** 편성 대상. 가족에서 빠졌거나 보호자 동의가 없으면 짜지 않는다(예외 → FAILED). */
    private ProfileDetails subjectOf(CoachRun run) {
        UUID subjectId = run.getSubjectProfileId();
        if (subjectId == null) throw new IllegalStateException("대상 프로필이 없는 옛 주간 실행이다: run=" + run.getId());
        ProfileDetails subject = profileQuery.findDetails(subjectId);
        if (subject == null || !subject.familyId().equals(run.getFamilyId())) {
            throw new IllegalStateException("편성 대상이 이 가족 구성원이 아니다: run=" + run.getId());
        }
        if (ParticipantConsent.missing(subject)) {
            throw new IllegalStateException("편성 대상의 보호자 동의가 없어 AI 요청을 만들지 않는다: run=" + run.getId());
        }
        return subject;
    }

    private static LocalDate requireRunDate(CoachRun run) {
        LocalDate runDate = run.getRunDate();
        if (runDate == null) throw new IllegalStateException("날짜가 없는 옛 주간 실행이다: run=" + run.getId());
        return runDate;
    }

    private static CoachRunConditions requireConditions(CoachRun run) {
        CoachRunConditions conditions = run.getConditions();
        if (conditions == null) throw new IllegalStateException("조건이 없는 옛 주간 실행이다: run=" + run.getId());
        return conditions;
    }
}
