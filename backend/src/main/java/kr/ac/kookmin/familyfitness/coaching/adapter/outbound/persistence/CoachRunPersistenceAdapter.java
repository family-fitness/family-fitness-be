package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@link CoachRunRepository} 의 JPA 구현. 도메인 ↔ 엔티티 변환은 여기서만 한다. */
@Repository
public class CoachRunPersistenceAdapter implements CoachRunRepository {
    private static final TypeReference<List<CoachStep>> STEPS = new TypeReference<>() {};
    private static final TypeReference<List<ProposalParticipant>> PARTICIPANTS = new TypeReference<>() {};
    private static final TypeReference<List<ProposalCitation>> CITATIONS = new TypeReference<>() {};

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
        CoachRunEntity existing = runs.findById(run.getId()).orElse(null);
        CoachRunEntity entity;
        if (existing == null) {
            entity = toEntity(run);
        } else {
            applyFrom(existing, run);
            entity = existing;
        }
        runs.save(entity);
        if (!run.getProposals().isEmpty()) {
            items.deleteByIdCoachRunId(run.getId());
            items.flush();
            items.saveAll(
                    run.getProposals().stream().map(it -> toEntity(it, run)).toList());
        }
        return run;
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
    public boolean existsByFamilyAndStatus(UUID familyId, CoachRunStatus status) {
        return runs.existsByFamilyIdAndStatus(familyId, status.name());
    }

    @Override
    public boolean existsByFamilyAndWeekAndStatusIn(
            UUID familyId, LocalDate weekStart, Collection<CoachRunStatus> statuses) {
        return runs.existsByFamilyIdAndWeekStartAndStatusIn(
                familyId, weekStart, statuses.stream().map(Enum::name).toList());
    }

    @Override
    public @Nullable CoachRun findLatestOfWeek(UUID familyId, LocalDate weekStart) {
        CoachRunEntity entity = runs.findFirstByFamilyIdAndWeekStartOrderByCreatedAtDesc(familyId, weekStart);
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
        return new CoachRunEntity(
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
    }

    private void applyFrom(CoachRunEntity entity, CoachRun run) {
        entity.setStatus(run.getStatus().name());
        entity.setSummary(run.getSummary());
        String steps = stepsJson(run);
        entity.setStepsJson(steps == null ? entity.getStepsJson() : steps);
        entity.setProposalJson(run.getProposalJson() == null ? entity.getProposalJson() : run.getProposalJson());
        entity.setAiRunId(run.getAiRunId());
        entity.setModelName(run.getModelName());
        entity.setApprovedBy(run.getApprovedBy());
        entity.setApprovedAt(run.getApprovedAt());
        entity.setRejectedReason(run.getRejectedReason());
        entity.setRejectedAt(run.getRejectedAt());
        entity.setFailureReason(run.getFailureReason());
        entity.setAiRefused(run.isAiRefused());
        entity.setAiRefusalReason(run.getAiRefusalReason());
        entity.setUpdatedAt(run.getUpdatedAt());
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
                e.getUpdatedAt());
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
