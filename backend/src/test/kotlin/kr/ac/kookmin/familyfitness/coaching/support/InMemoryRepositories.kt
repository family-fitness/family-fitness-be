package kr.ac.kookmin.familyfitness.coaching.support

import kr.ac.kookmin.familyfitness.coaching.application.port.CoachMessageRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.VideoInteractionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo
import kr.ac.kookmin.familyfitness.coaching.domain.Mission
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 애플리케이션 테스트용 인메모리 포트 구현. 도메인 객체를 그대로 보관한다(같은 인스턴스).
 * 조건부 UPDATE 는 저장된 상태 스냅샷으로 흉내 낸다.
 */
class InMemoryCoachRunRepository : CoachRunRepository {
    val runs = ConcurrentHashMap<UUID, CoachRun>()
    private val persistedStatus = ConcurrentHashMap<UUID, CoachRunStatus>()

    override fun save(run: CoachRun): CoachRun {
        runs[run.id] = run
        persistedStatus[run.id] = run.status
        return run
    }

    override fun findById(id: UUID): CoachRun? = runs[id]

    override fun currentStatus(id: UUID): CoachRunStatus? = persistedStatus[id]

    override fun existsByFamilyAndStatus(
        familyId: UUID,
        status: CoachRunStatus,
    ): Boolean = runs.values.any { it.familyId == familyId && persistedStatus[it.id] == status }

    override fun existsByFamilyAndWeekAndStatusIn(
        familyId: UUID,
        weekStart: LocalDate,
        statuses: Collection<CoachRunStatus>,
    ): Boolean = runs.values.any { it.familyId == familyId && it.weekStart == weekStart && persistedStatus[it.id] in statuses }

    override fun findLatestOfWeek(
        familyId: UUID,
        weekStart: LocalDate,
    ): CoachRun? = runs.values.filter { it.familyId == familyId && it.weekStart == weekStart }.maxByOrNull { it.createdAt }

    override fun approveIfAwaiting(run: CoachRun): Boolean = transition(run, CoachRunStatus.APPROVED)

    override fun rejectIfAwaiting(run: CoachRun): Boolean = transition(run, CoachRunStatus.REJECTED)

    private fun transition(
        run: CoachRun,
        to: CoachRunStatus,
    ): Boolean {
        if (persistedStatus[run.id] != CoachRunStatus.AWAITING_APPROVAL) return false
        persistedStatus[run.id] = to
        runs[run.id] = run
        return true
    }
}

class InMemoryMissionRepository : MissionRepository {
    val missions = ConcurrentHashMap<UUID, Mission>()
    var saveCount = 0

    override fun save(mission: Mission): Mission {
        saveCount++
        missions[mission.id] = mission
        return mission
    }

    override fun findById(id: UUID): Mission? = missions[id]

    override fun findByFamily(familyId: UUID): List<Mission> = missions.values.filter { it.familyId == familyId }

    override fun findOverlapping(
        familyId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<Mission> = findByFamily(familyId).filter { it.overlaps(from, to) }

    override fun countByCoachRun(coachRunId: UUID): Int = missions.values.count { it.coachRunId == coachRunId }
}

class InMemoryExerciseVideoRepository(
    videos: List<ExerciseVideo> = emptyList(),
) : ExerciseVideoRepository {
    val videos = videos.associateBy { it.videoId }.toMutableMap()

    override fun findById(videoId: String): ExerciseVideo? = videos[videoId]

    override fun findAllByIds(videoIds: Collection<String>): List<ExerciseVideo> = videoIds.mapNotNull { videos[it] }

    override fun findAllAfter(afterVideoId: String?): List<ExerciseVideo> =
        videos.values.filter { afterVideoId == null || it.videoId > afterVideoId }.sortedBy { it.videoId }
}

class InMemoryVideoInteractionRepository : VideoInteractionRepository {
    val interactions = ConcurrentHashMap<UUID, VideoInteraction>()

    override fun find(
        profileId: UUID,
        videoId: String,
    ): VideoInteraction? = interactions.values.firstOrNull { it.profileId == profileId && it.videoId == videoId }

    override fun save(interaction: VideoInteraction): VideoInteraction {
        interactions[interaction.id] = interaction
        return interaction
    }

    override fun findAllOf(profileId: UUID): List<VideoInteraction> = interactions.values.filter { it.profileId == profileId }
}

class InMemoryCoachMessageRepository : CoachMessageRepository {
    val messages = mutableListOf<CoachMessage>()

    override fun save(message: CoachMessage): CoachMessage {
        messages += message
        return message
    }

    override fun ownerOfConversation(conversationId: UUID): UUID? =
        messages.filter { it.conversationId == conversationId }.minByOrNull { it.createdAt }?.profileId
}
