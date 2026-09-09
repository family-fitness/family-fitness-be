package kr.ac.kookmin.familyfitness.coaching.adapter.persistence

import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.Mission
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantStatus
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** [MissionRepository] 의 JPA 구현. 미션 본문은 불변이고 참여자 행만 갱신된다. */
@Repository
class MissionPersistenceAdapter(
    private val missions: MissionJpaRepository,
    private val participants: MissionParticipantJpaRepository,
) : MissionRepository {
    override fun save(mission: Mission): Mission {
        if (!missions.existsById(mission.id)) missions.save(mission.toEntity())
        val existing = participants.findByIdMissionId(mission.id).associateBy { it.id.profileId }
        participants.saveAll(
            mission.participants.map { p ->
                existing[p.profileId]?.also { it.applyFrom(p) } ?: p.toEntity(mission.id)
            },
        )
        return mission
    }

    override fun findById(id: UUID): Mission? = missions.findById(id).orElse(null)?.let { toDomain(it, participants.findByIdMissionId(id)) }

    override fun findByFamily(familyId: UUID): List<Mission> = assemble(missions.findByFamilyId(familyId))

    override fun findOverlapping(
        familyId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<Mission> = assemble(missions.findByFamilyIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(familyId, to, from))

    override fun countByCoachRun(coachRunId: UUID): Int = missions.countByCoachRunId(coachRunId).toInt()

    private fun assemble(entities: List<MissionEntity>): List<Mission> {
        if (entities.isEmpty()) return emptyList()
        val byMission = participants.findByIdMissionIdIn(entities.map { it.id }).groupBy { it.id.missionId }
        return entities.map { toDomain(it, byMission[it.id].orEmpty()) }
    }

    private fun Mission.toEntity(): MissionEntity =
        MissionEntity(
            id = id,
            familyId = familyId,
            coachRunId = coachRunId,
            title = title,
            description = description,
            origin = origin.name,
            targetMetric = targetMetric.name,
            targetValue = targetValue,
            videoId = video?.videoId,
            videoStartSec = video?.startSec,
            rationale = rationale,
            startsOn = startsOn,
            endsOn = endsOn,
            createdBy = createdBy,
            createdAt = createdAt,
        )

    private fun MissionParticipant.toEntity(missionId: UUID): MissionParticipantEntity =
        MissionParticipantEntity(
            id = MissionParticipantId(missionId, profileId),
            status = status.name,
            progress = ratio(progress),
            verifiedBy = verifiedBy?.name,
            verifiedAt = verifiedAt,
            confirmedByProfileId = confirmedBy,
            coachRole = coachRole,
            updatedAt = updatedAt,
        )

    private fun MissionParticipantEntity.applyFrom(p: MissionParticipant) {
        status = p.status.name
        progress = ratio(p.progress)
        verifiedBy = p.verifiedBy?.name
        verifiedAt = p.verifiedAt
        confirmedByProfileId = p.confirmedBy
        updatedAt = p.updatedAt
    }

    private fun toDomain(
        e: MissionEntity,
        ps: List<MissionParticipantEntity>,
    ): Mission =
        Mission.reconstitute(
            id = e.id,
            familyId = e.familyId,
            coachRunId = e.coachRunId,
            title = e.title,
            description = e.description,
            origin = MissionOrigin.valueOf(e.origin),
            targetMetric = TargetMetric.valueOf(e.targetMetric),
            targetValue = e.targetValue,
            video = e.videoId?.let { MissionVideo(it, e.videoStartSec) },
            rationale = e.rationale,
            startsOn = e.startsOn,
            endsOn = e.endsOn,
            createdBy = e.createdBy,
            createdAt = e.createdAt,
            participants =
                ps.map { p ->
                    MissionParticipant.reconstitute(
                        profileId = p.id.profileId,
                        coachRole = p.coachRole,
                        progress = p.progress.toDouble(),
                        status = ParticipantStatus.valueOf(p.status),
                        verifiedBy = p.verifiedBy?.let(VerifiedBy::valueOf),
                        verifiedAt = p.verifiedAt,
                        confirmedBy = p.confirmedByProfileId,
                        updatedAt = p.updatedAt,
                    )
                },
        )

    private fun ratio(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(3, RoundingMode.DOWN)
}
