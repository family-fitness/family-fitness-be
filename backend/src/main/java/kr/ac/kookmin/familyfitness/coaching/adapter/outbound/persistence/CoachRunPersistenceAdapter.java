package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@link CoachRunRepository} 의 JPA 구현. 도메인 ↔ 엔티티 변환은 여기서만 한다. */
@Repository
public class CoachRunPersistenceAdapter implements CoachRunRepository {
    private static final TypeReference<List<CoachStep>> STEPS = new TypeReference<>() {};
    private static final TypeReference<List<ProposalParticipant>> PARTICIPANTS = new TypeReference<>() {};
    private static final TypeReference<List<ProposalCitation>> CITATIONS = new TypeReference<>() {};

    /** V134 의 (프로필, 날짜) 잠금 인덱스. 위반 오류 문구에 이 이름이 실린다(H2 · PostgreSQL 모두). */
    static final String LOCK_INDEX = "ux_coach_runs_lock_key";

    private final CoachRunJpaRepository runs;
    private final CoachRunProposalItemJpaRepository items;
    private final JsonMapper jsonMapper;

    public CoachRunPersistenceAdapter(
            CoachRunJpaRepository runs, CoachRunProposalItemJpaRepository items, JsonMapper jsonMapper) {
        this.runs = runs;
        this.items = items;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public CoachRun save(CoachRun run) {
        if (runs.existsById(run.getId())) {
            throw new IllegalStateException("이미 있는 실행은 조건부 전이로만 바꾼다: run=" + run.getId());
        }
        runs.save(toEntity(run));
        replaceItems(run);
        return run;
    }

    @Override
    public boolean attachAiRunIfRunning(CoachRun run) {
        if (run.getStatus() != CoachRunStatus.RUNNING) {
            throw new IllegalStateException("도메인 attachAiRun 후에 불러야 한다");
        }
        return runs.attachAiRunIfRunning(run.getId(), Objects.requireNonNull(run.getAiRunId()), run.getUpdatedAt())
                == 1;
    }

    @Override
    public boolean finishIfRunning(CoachRun run) {
        if (run.getStatus() != CoachRunStatus.AWAITING_APPROVAL && run.getStatus() != CoachRunStatus.FAILED) {
            throw new IllegalStateException("도메인 complete · fail 후에 불러야 한다");
        }
        boolean finished = runs.finishIfRunning(
                        run.getId(),
                        run.getStatus().name(),
                        run.getSummary(),
                        stepsJson(run),
                        run.getProposalJson(),
                        run.getModelName(),
                        codeName(run.getFailureCode()),
                        run.getFailureReason(),
                        run.isAiRefused(),
                        run.getAiRefusalReason(),
                        run.getUpdatedAt())
                == 1;
        if (finished) replaceItems(run);
        return finished;
    }

    private void replaceItems(CoachRun run) {
        if (run.getProposals().isEmpty()) return;
        items.deleteByIdCoachRunId(run.getId());
        items.flush();
        items.saveAll(run.getProposals().stream().map(it -> toEntity(it, run)).toList());
    }

    /**
     * 곧바로 flush 해 유니크 인덱스 위반을 여기서 받는다. 잠금 인덱스 위반이면 false, 다른 제약 위반은 그대로 던진다.
     * 위반 뒤 트랜잭션은 롤백 전용이 되므로 호출자는 예외를 던져 끝내야 한다(편성 시작은 409 로 끝낸다).
     */
    @Override
    public boolean insertRunning(CoachRun run) {
        try {
            runs.saveAndFlush(toEntity(run));
            return true;
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(LOCK_INDEX)) return false;
            throw e;
        }
    }

    @Override
    public @Nullable CoachRun findById(UUID id) {
        CoachRunEntity entity = runs.findById(id).orElse(null);
        return entity == null ? null : toDomain(entity, items.findByIdCoachRunIdOrderByIdPosition(id));
    }

    @Override
    public @Nullable CoachRunStatus currentStatus(UUID id) {
        String status = runs.statusOf(id);
        return status == null ? null : CoachRunStatus.valueOf(status);
    }

    @Override
    public boolean isLocked(String lockKey) {
        return runs.existsByLockKey(lockKey);
    }

    @Override
    public int failStaleLock(String lockKey, Instant before, String reason, Instant at) {
        return runs.failStaleLock(lockKey, before, take(reason, CoachRun.MAX_REASON), at);
    }

    @Override
    public int failRunningCreatedBefore(Instant before, String reason, Instant at) {
        return runs.failRunningCreatedBefore(before, take(reason, CoachRun.MAX_REASON), at);
    }

    private static String take(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }

    @Override
    public int rejectAwaitingOf(UUID subjectProfileId, LocalDate runDate, String reason, Instant at) {
        return runs.rejectAwaitingOf(subjectProfileId, runDate, take(reason, CoachRun.MAX_REASON), at);
    }

    @Override
    public @Nullable CoachRun findLatestOfWeek(UUID familyId, LocalDate weekStart) {
        return withItems(runs.findFirstByFamilyIdAndWeekStartOrderByCreatedAtDesc(familyId, weekStart));
    }

    @Override
    public @Nullable CoachRun findLatestOfFamily(UUID familyId) {
        return withItems(runs.findFirstByFamilyIdOrderByCreatedAtDesc(familyId));
    }

    @Override
    public @Nullable CoachRun findLatestOfSubject(UUID familyId, UUID subjectProfileId) {
        return withItems(runs.findFirstByFamilyIdAndSubjectProfileIdOrderByCreatedAtDesc(familyId, subjectProfileId));
    }

    private @Nullable CoachRun withItems(@Nullable CoachRunEntity entity) {
        return entity == null ? null : toDomain(entity, items.findByIdCoachRunIdOrderByIdPosition(entity.getId()));
    }

    @Override
    public boolean approveIfAwaiting(CoachRun run) {
        if (run.getStatus() != CoachRunStatus.APPROVED) {
            throw new IllegalStateException("도메인 승인 후에 불러야 한다");
        }
        return runs.approveIfAwaiting(
                        run.getId(),
                        Objects.requireNonNull(run.getApprovedBy()),
                        Objects.requireNonNull(run.getApprovedAt()))
                == 1;
    }

    @Override
    public boolean rejectIfAwaiting(CoachRun run) {
        if (run.getStatus() != CoachRunStatus.REJECTED) {
            throw new IllegalStateException("도메인 거절 후에 불러야 한다");
        }
        return runs.rejectIfAwaiting(
                        run.getId(),
                        run.getRejectedReason(),
                        run.getRejectedAt() == null ? run.getUpdatedAt() : run.getRejectedAt())
                == 1;
    }

    private CoachRunEntity toEntity(CoachRun run) {
        CoachRunEntity entity = new CoachRunEntity(
                run.getId(),
                run.getFamilyId(),
                run.getWeekStart(),
                run.getTriggerType().name(),
                run.getStatus().name(),
                run.getSummary(),
                stepsJson(run),
                run.getProposalJson(),
                run.getAiRunId(),
                run.getModelName(),
                run.getDaysPerWeek(),
                run.getMinutesPerSession(),
                run.getRequestedBy(),
                run.getApprovedBy(),
                run.getApprovedAt(),
                run.getRejectedReason(),
                run.getRejectedAt(),
                run.getFailureReason(),
                run.isAiRefused(),
                run.getAiRefusalReason(),
                run.getCreatedAt(),
                run.getUpdatedAt());
        CoachRunConditions conditions = run.getConditions();
        entity.setRequest(
                run.getSubjectProfileId(),
                run.getRunDate(),
                conditions == null ? null : conditions.quiet(),
                conditions == null || conditions.place() == null
                        ? null
                        : conditions.place().name(),
                conditions == null || conditions.focusFactor() == null
                        ? null
                        : conditions.focusFactor().name(),
                conditions == null ? null : conditions.withParent());
        entity.setLockKey(run.lockKey());
        entity.setFailureCode(codeName(run.getFailureCode()));
        return entity;
    }

    private static @Nullable String codeName(@Nullable CoachRunFailureCode code) {
        return code == null ? null : code.name();
    }

    /** 모르는 값(다른 버전이 쓴 코드)은 응답이 깨지지 않게 ERROR 로 읽는다. */
    private static @Nullable CoachRunFailureCode codeOf(@Nullable String name) {
        if (name == null) return null;
        try {
            return CoachRunFailureCode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CoachRunFailureCode.ERROR;
        }
    }

    private @Nullable String stepsJson(CoachRun run) {
        return run.getSteps().isEmpty() ? null : jsonMapper.writeValueAsString(run.getSteps());
    }

    private CoachRunProposalItemEntity toEntity(CoachProposalItem item, CoachRun run) {
        ProposalVideo video = item.video();
        return new CoachRunProposalItemEntity(
                new ProposalItemId(run.getId(), item.position()),
                item.title(),
                item.description(),
                item.rationale(),
                item.targetMetric(),
                item.targetValue(),
                video == null ? null : video.videoId(),
                video == null ? null : video.startSec(),
                item.startsOn() == null ? run.getWeekStart() : item.startsOn(),
                item.endsOn() == null ? run.getWeekEnd() : item.endsOn(),
                item.copyChild(),
                item.copyParent(),
                jsonMapper.writeValueAsString(item.participants()),
                jsonMapper.writeValueAsString(item.citations()));
    }

    private CoachRun toDomain(CoachRunEntity e, List<CoachRunProposalItemEntity> itemEntities) {
        String stepsJson = e.getStepsJson();
        return CoachRun.reconstitute(
                e.getId(),
                e.getFamilyId(),
                e.getWeekStart(),
                TriggerType.valueOf(e.getTriggerType()),
                e.getDaysPerWeek(),
                e.getMinutesPerSession(),
                e.getRequestedByProfileId(),
                e.getCreatedAt(),
                e.getSubjectProfileId(),
                e.getRunDate(),
                conditionsOf(e),
                CoachRunStatus.valueOf(e.getStatus()),
                itemEntities.stream().map(this::toDomain).toList(),
                stepsJson == null ? List.of() : jsonMapper.readValue(stepsJson, STEPS),
                e.getSummary(),
                e.getProposalJson(),
                e.getAiRunId(),
                e.getModelName(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getRejectedReason(),
                e.getRejectedAt(),
                e.getFailureReason(),
                e.isAiRefused(),
                e.getAiRefusalReason(),
                codeOf(e.getFailureCode()),
                e.getUpdatedAt());
    }

    /** 옛 주간 실행 행(대상이 없음)은 조건도 없다. */
    private static @Nullable CoachRunConditions conditionsOf(CoachRunEntity e) {
        if (e.getSubjectProfileId() == null) return null;
        String place = e.getPlace();
        String focusFactor = e.getFocusFactor();
        return new CoachRunConditions(
                e.getMinutesPerSession(),
                Boolean.TRUE.equals(e.getQuiet()),
                place == null ? null : CoachPlace.valueOf(place),
                focusFactor == null ? null : FitnessFactor.valueOf(focusFactor),
                Boolean.TRUE.equals(e.getWithParent()));
    }

    private CoachProposalItem toDomain(CoachRunProposalItemEntity e) {
        String videoId = e.getVideoId();
        return new CoachProposalItem(
                e.getId().getPosition(),
                e.getTitle(),
                e.getTargetMetric(),
                e.getTargetValue(),
                e.getRationale(),
                e.getDescription(),
                e.getStartsOn(),
                e.getEndsOn(),
                jsonMapper.readValue(e.getParticipantsJson(), PARTICIPANTS),
                videoId == null ? null : new ProposalVideo(videoId, e.getVideoStartSec()),
                jsonMapper.readValue(e.getCitationsJson(), CITATIONS),
                e.getCopyChild(),
                e.getCopyParent());
    }
}
