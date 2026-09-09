package kr.ac.kookmin.familyfitness.coaching.application.port

import kr.ac.kookmin.familyfitness.coaching.domain.CoachMessage
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo
import kr.ac.kookmin.familyfitness.coaching.domain.Mission
import kr.ac.kookmin.familyfitness.coaching.domain.VideoInteraction
import java.time.LocalDate
import java.util.UUID

/** 코치 실행 저장소(아웃바운드 포트). 제안 항목은 실행과 함께 저장·복원된다. */
interface CoachRunRepository {
    fun save(run: CoachRun): CoachRun

    fun findById(id: UUID): CoachRun?

    fun currentStatus(id: UUID): CoachRunStatus?

    fun existsByFamilyAndStatus(
        familyId: UUID,
        status: CoachRunStatus,
    ): Boolean

    fun existsByFamilyAndWeekAndStatusIn(
        familyId: UUID,
        weekStart: LocalDate,
        statuses: Collection<CoachRunStatus>,
    ): Boolean

    /** 그 주(weekStart) 실행 중 가장 최근 것. */
    fun findLatestOfWeek(
        familyId: UUID,
        weekStart: LocalDate,
    ): CoachRun?

    /**
     * 승인 결과를 조건부 UPDATE(`where status = 'AWAITING_APPROVAL'`) 한 문장으로 반영한다.
     * 동시 승인 경합에서 한쪽만 1행을 바꾼다. 0행이면 false.
     */
    fun approveIfAwaiting(run: CoachRun): Boolean

    fun rejectIfAwaiting(run: CoachRun): Boolean
}

interface MissionRepository {
    fun save(mission: Mission): Mission

    fun findById(id: UUID): Mission?

    fun findByFamily(familyId: UUID): List<Mission>

    /** [from]~[to](양끝 포함)와 기간이 겹치는 가족 미션. */
    fun findOverlapping(
        familyId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<Mission>

    fun countByCoachRun(coachRunId: UUID): Int
}

interface ExerciseVideoRepository {
    fun findById(videoId: String): ExerciseVideo?

    fun findAllByIds(videoIds: Collection<String>): List<ExerciseVideo>

    /** videoId 오름차순, [afterVideoId] 보다 큰 것부터. 카탈로그가 작아 필터는 메모리에서 한다(▲ 커지면 SQL 필터). */
    fun findAllAfter(afterVideoId: String?): List<ExerciseVideo>
}

interface VideoInteractionRepository {
    fun find(
        profileId: UUID,
        videoId: String,
    ): VideoInteraction?

    fun save(interaction: VideoInteraction): VideoInteraction

    fun findAllOf(profileId: UUID): List<VideoInteraction>
}

interface CoachMessageRepository {
    fun save(message: CoachMessage): CoachMessage

    /** 대화의 주인 프로필. 대화가 없으면 null. */
    fun ownerOfConversation(conversationId: UUID): UUID?
}
