package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence

import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo
import kr.ac.kookmin.familyfitness.coaching.domain.TriggerType
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID

/** [CoachRunRepository] 의 JPA 구현. 도메인 ↔ 엔티티 변환은 여기서만 한다. */
@Repository
class CoachRunPersistenceAdapter(
    private val runs: CoachRunJpaRepository,
    private val items: CoachRunProposalItemJpaRepository,
    private val jsonMapper: JsonMapper,
) : CoachRunRepository {
    override fun save(run: CoachRun): CoachRun {
        val entity = runs.findById(run.id).orElse(null)?.also { it.applyFrom(run) } ?: run.toEntity()
        runs.save(entity)
        if (run.proposals.isNotEmpty()) {
            items.deleteByIdCoachRunId(run.id)
            items.flush()
            items.saveAll(run.proposals.map { it.toEntity(run) })
        }
        return run
    }

    override fun findById(id: UUID): CoachRun? =
        runs.findById(id).orElse(null)?.let { toDomain(it, items.findByIdCoachRunIdOrderByIdPosition(id)) }

    override fun currentStatus(id: UUID): CoachRunStatus? = runs.statusOf(id)?.let(CoachRunStatus::valueOf)

    override fun existsByFamilyAndStatus(
        familyId: UUID,
        status: CoachRunStatus,
    ): Boolean = runs.existsByFamilyIdAndStatus(familyId, status.name)

    override fun existsByFamilyAndWeekAndStatusIn(
        familyId: UUID,
        weekStart: LocalDate,
        statuses: Collection<CoachRunStatus>,
    ): Boolean = runs.existsByFamilyIdAndWeekStartAndStatusIn(familyId, weekStart, statuses.map { it.name })

    override fun findLatestOfWeek(
        familyId: UUID,
        weekStart: LocalDate,
    ): CoachRun? =
        runs.findFirstByFamilyIdAndWeekStartOrderByCreatedAtDesc(familyId, weekStart)?.let {
            toDomain(it, items.findByIdCoachRunIdOrderByIdPosition(it.id))
        }

    override fun approveIfAwaiting(run: CoachRun): Boolean {
        check(run.status == CoachRunStatus.APPROVED) { "도메인 승인 후에 불러야 한다" }
        return runs.approveIfAwaiting(run.id, checkNotNull(run.approvedBy), checkNotNull(run.approvedAt)) == 1
    }

    override fun rejectIfAwaiting(run: CoachRun): Boolean {
        check(run.status == CoachRunStatus.REJECTED) { "도메인 거절 후에 불러야 한다" }
        return runs.rejectIfAwaiting(run.id, run.rejectedReason, run.rejectedAt ?: run.updatedAt) == 1
    }

    private fun CoachRun.toEntity(): CoachRunEntity =
        CoachRunEntity(
            id = id,
            familyId = familyId,
            weekStart = weekStart,
            triggerType = triggerType.name,
            status = status.name,
            summary = summary,
            stepsJson = steps.takeIf { it.isNotEmpty() }?.let(jsonMapper::writeValueAsString),
            proposalJson = proposalJson,
            aiRunId = aiRunId,
            modelName = modelName,
            daysPerWeek = daysPerWeek,
            minutesPerSession = minutesPerSession,
            requestedByProfileId = requestedBy,
            approvedBy = approvedBy,
            approvedAt = approvedAt,
            rejectedReason = rejectedReason,
            rejectedAt = rejectedAt,
            failureReason = failureReason,
            aiRefused = aiRefused,
            aiRefusalReason = aiRefusalReason,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun CoachRunEntity.applyFrom(run: CoachRun) {
        status = run.status.name
        summary = run.summary
        stepsJson = run.steps.takeIf { it.isNotEmpty() }?.let(jsonMapper::writeValueAsString) ?: stepsJson
        proposalJson = run.proposalJson ?: proposalJson
        aiRunId = run.aiRunId
        modelName = run.modelName
        approvedBy = run.approvedBy
        approvedAt = run.approvedAt
        rejectedReason = run.rejectedReason
        rejectedAt = run.rejectedAt
        failureReason = run.failureReason
        aiRefused = run.aiRefused
        aiRefusalReason = run.aiRefusalReason
        updatedAt = run.updatedAt
    }

    private fun CoachProposalItem.toEntity(run: CoachRun): CoachRunProposalItemEntity =
        CoachRunProposalItemEntity(
            id = ProposalItemId(run.id, position),
            title = title,
            description = description,
            rationale = rationale,
            targetMetric = targetMetric,
            targetValue = targetValue,
            videoId = video?.videoId,
            videoStartSec = video?.startSec,
            startsOn = startsOn ?: run.weekStart,
            endsOn = endsOn ?: run.weekEnd,
            copyChild = copyChild,
            copyParent = copyParent,
            participantsJson = jsonMapper.writeValueAsString(participants),
            citationsJson = jsonMapper.writeValueAsString(citations),
        )

    private fun toDomain(
        e: CoachRunEntity,
        itemEntities: List<CoachRunProposalItemEntity>,
    ): CoachRun =
        CoachRun.reconstitute(
            id = e.id,
            familyId = e.familyId,
            weekStart = e.weekStart,
            triggerType = TriggerType.valueOf(e.triggerType),
            daysPerWeek = e.daysPerWeek,
            minutesPerSession = e.minutesPerSession,
            requestedBy = e.requestedByProfileId,
            createdAt = e.createdAt,
            status = CoachRunStatus.valueOf(e.status),
            proposals = itemEntities.map { it.toDomain() },
            steps = e.stepsJson?.let { jsonMapper.readValue(it, STEPS) } ?: emptyList(),
            summary = e.summary,
            proposalJson = e.proposalJson,
            aiRunId = e.aiRunId,
            modelName = e.modelName,
            approvedBy = e.approvedBy,
            approvedAt = e.approvedAt,
            rejectedReason = e.rejectedReason,
            rejectedAt = e.rejectedAt,
            failureReason = e.failureReason,
            aiRefused = e.aiRefused,
            aiRefusalReason = e.aiRefusalReason,
            updatedAt = e.updatedAt,
        )

    private fun CoachRunProposalItemEntity.toDomain(): CoachProposalItem =
        CoachProposalItem(
            position = id.position,
            title = title,
            targetMetric = targetMetric,
            targetValue = targetValue,
            rationale = rationale,
            description = description,
            startsOn = startsOn,
            endsOn = endsOn,
            participants = jsonMapper.readValue(participantsJson, PARTICIPANTS),
            video = videoId?.let { ProposalVideo(it, videoStartSec) },
            citations = jsonMapper.readValue(citationsJson, CITATIONS),
            copyChild = copyChild,
            copyParent = copyParent,
        )

    companion object {
        private val STEPS = object : TypeReference<List<CoachStep>>() {}
        private val PARTICIPANTS = object : TypeReference<List<ProposalParticipant>>() {}
        private val CITATIONS = object : TypeReference<List<ProposalCitation>>() {}
    }
}
