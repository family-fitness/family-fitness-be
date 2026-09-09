package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository
import kr.ac.kookmin.familyfitness.coaching.domain.Mission
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException
import kr.ac.kookmin.familyfitness.coaching.domain.MissionOrigin
import kr.ac.kookmin.familyfitness.coaching.domain.MissionStatus
import kr.ac.kookmin.familyfitness.coaching.domain.MissionVideo
import kr.ac.kookmin.familyfitness.coaching.domain.NotFamilyMemberException
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateMissionCommand(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val targetMetric: TargetMetric,
    val targetValue: Int,
    val videoId: String?,
    val participantProfileIds: List<UUID>,
)

data class MissionCreatedView(
    val missionId: UUID,
    val origin: MissionOrigin,
    val coachRunId: UUID?,
    val serverVerifiable: Boolean,
)

enum class MissionScope {
    ALL,
    MINE,
    FAMILY,
}

data class MissionListView(
    val missions: List<MissionView>,
)

data class MissionView(
    val missionId: UUID,
    val title: String,
    val origin: MissionOrigin,
    val coachRunId: UUID?,
    val targetMetric: TargetMetric,
    val targetValue: Int,
    val serverVerifiable: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rationale: String?,
    val video: MissionVideoView?,
    val participants: List<MissionParticipantView>,
)

data class MissionVideoView(
    val videoId: String,
    val title: String?,
    val url: String,
    val durationSec: Int?,
    val startSec: Int?,
)

data class MissionParticipantView(
    val profileId: UUID,
    val name: String?,
    val progress: Double,
    val completed: Boolean,
    val verifiedBy: VerifiedBy?,
    val needsGuardianCheck: Boolean,
)

data class ConfirmParticipantView(
    val missionId: UUID,
    val profileId: UUID,
    val completed: Boolean,
    val verifiedBy: VerifiedBy?,
    val confirmedBy: UUID?,
    val verifiedAt: Instant?,
)

/** 미션 직접 만들기 · 목록 · 보호자 확인. */
@Service
class MissionService(
    private val missions: MissionRepository,
    private val videos: ExerciseVideoRepository,
    private val familyAccess: FamilyAccess,
    private val profileQuery: ProfileQuery,
    private val policy: MissionCompletionPolicy,
    private val time: AppTime,
) {
    @Transactional
    fun create(
        userId: UUID,
        familyId: UUID,
        command: CreateMissionCommand,
    ): MissionCreatedView {
        val parent = familyAccess.requireParent(userId, familyId)
        val memberIds = profileQuery.summariesOfFamily(familyId).map { it.profileId }.toSet()
        command.participantProfileIds.firstOrNull { it !in memberIds }?.let { throw NotFamilyMemberException(it) }
        val video =
            command.videoId?.let { id ->
                videos.findById(id) ?: throw VideoNotFoundException(id)
                MissionVideo(id, null)
            }
        val mission =
            missions.save(
                Mission.manual(
                    id = UUID.randomUUID(),
                    familyId = familyId,
                    title = command.title,
                    targetMetric = command.targetMetric,
                    targetValue = command.targetValue,
                    video = video,
                    startsOn = command.startDate,
                    endsOn = command.endDate,
                    participantProfileIds = command.participantProfileIds,
                    createdBy = parent.profileId,
                    at = time.now(),
                ),
            )
        return MissionCreatedView(mission.id, mission.origin, mission.coachRunId, mission.serverVerifiable)
    }

    /**
     * 가족 미션 목록. 읽을 때 미완료 참여자의 진행도를 다시 계산해 바뀐 것만 저장한다(write-through).
     * `MINE` = 호출 계정의 이 가족 프로필이 참여자 · `FAMILY` = 참여자 2명 이상.
     */
    @Transactional
    fun list(
        userId: UUID,
        familyId: UUID,
        scope: MissionScope,
        status: MissionStatus?,
    ): MissionListView {
        val caller = familyAccess.requireMember(userId, familyId)
        val today = time.today()
        val now = time.now()
        val names = profileQuery.summariesOfFamily(familyId).associate { it.profileId to it.name }
        val all = missions.findByFamily(familyId).map { policy.refreshAll(it, now) }
        val filtered =
            all
                .filter {
                    when (scope) {
                        MissionScope.ALL -> true
                        MissionScope.MINE -> it.isParticipant(caller.profileId)
                        MissionScope.FAMILY -> it.participants.size >= 2
                    }
                }.filter { status == null || it.statusOn(today) == status }
        val videoById =
            filtered
                .mapNotNull { it.video?.videoId }
                .toSet()
                .takeIf { it.isNotEmpty() }
                ?.let { ids -> videos.findAllByIds(ids).associateBy { it.videoId } }
                .orEmpty()
        return MissionListView(
            filtered.sortedWith(compareByDescending<Mission> { it.startsOn }.thenBy { it.createdAt }).map { m ->
                MissionView(
                    missionId = m.id,
                    title = m.title,
                    origin = m.origin,
                    coachRunId = m.coachRunId,
                    targetMetric = m.targetMetric,
                    targetValue = m.targetValue,
                    serverVerifiable = m.serverVerifiable,
                    startDate = m.startsOn,
                    endDate = m.endsOn,
                    rationale = m.rationale,
                    video =
                        m.video?.let { v ->
                            val video = videoById[v.videoId]
                            MissionVideoView(
                                videoId = v.videoId,
                                title = video?.title,
                                url = video?.url ?: "https://www.youtube.com/watch?v=${v.videoId}",
                                durationSec = video?.durationSec,
                                startSec = v.startSec,
                            )
                        },
                    participants =
                        m.participants.map { p ->
                            MissionParticipantView(
                                p.profileId,
                                names[p.profileId],
                                p.progress,
                                p.completed,
                                p.verifiedBy,
                                p.needsGuardianCheck,
                            )
                        },
                )
            },
        )
    }

    /** 보호자 확인(STEPS 등 사람이 말한 값). 목표 도달 전이면 422 `TARGET_NOT_REACHED`. */
    @Transactional
    fun confirm(
        userId: UUID,
        missionId: UUID,
        profileId: UUID,
    ): ConfirmParticipantView {
        val mission = missions.findById(missionId) ?: throw MissionNotFoundException(missionId)
        val parent = familyAccess.requireParent(userId, mission.familyId)
        mission.participantOf(profileId)
        val now = time.now()
        val refreshed = policy.refreshParticipant(mission, profileId, now)
        val participant = refreshed.confirm(profileId, parent.profileId, now)
        missions.save(refreshed)
        return ConfirmParticipantView(
            missionId = refreshed.id,
            profileId = profileId,
            completed = participant.completed,
            verifiedBy = participant.verifiedBy,
            confirmedBy = participant.confirmedBy,
            verifiedAt = participant.verifiedAt,
        )
    }
}
