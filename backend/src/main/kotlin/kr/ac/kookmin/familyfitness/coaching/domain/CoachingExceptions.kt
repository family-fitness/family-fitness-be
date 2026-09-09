package kr.ac.kookmin.familyfitness.coaching.domain

import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind
import java.time.LocalDate
import java.util.UUID

/** 승인되지 않은 실행의 제안을 미션으로 만들려 했다. 애플리케이션이 절대 만들면 안 되는 상태. */
class CoachApprovalRequiredException(
    message: String = "승인 전에는 제안을 미션으로 만들 수 없습니다",
) : DomainException("INVALID_STATE", ErrorKind.CONFLICT, message)

class ParentRoleRequiredException(
    message: String = "보호자만 승인·거절할 수 있습니다",
) : DomainException("NOT_A_PARENT", ErrorKind.FORBIDDEN, message)

class CoachFamilyAccessDeniedException(
    message: String = "다른 가족의 코치 실행입니다",
) : DomainException("NOT_SAME_FAMILY", ErrorKind.FORBIDDEN, message)

/** 이미 승인·거절·실패로 끝난 실행을 다시 결정하려 했다. 승인 후면 `ALREADY_APPROVED`, 그 외는 `INVALID_STATE`. */
class CoachRunAlreadyDecidedException(
    val currentStatus: CoachRunStatus,
) : DomainException(
        if (currentStatus == CoachRunStatus.APPROVED) "ALREADY_APPROVED" else "INVALID_STATE",
        ErrorKind.CONFLICT,
        "이미 결정된 코치 실행입니다: $currentStatus",
    )

class CoachRunInProgressException(
    familyId: UUID,
) : DomainException("RUN_IN_PROGRESS", ErrorKind.CONFLICT, "실행 중인 코치 실행이 있습니다: family=$familyId")

class AlreadyRunThisWeekException(
    weekStart: LocalDate,
) : DomainException("ALREADY_RUN_THIS_WEEK", ErrorKind.CONFLICT, "이번 주($weekStart) 편성이 이미 있습니다")

class NoMeasuredMemberException(
    familyId: UUID,
) : DomainException("NO_MEASURED_MEMBER", ErrorKind.RULE_VIOLATION, "측정 기록이 있는 구성원이 없습니다: family=$familyId")

class NotFamilyMemberException(
    profileId: UUID,
) : DomainException("NOT_FAMILY_MEMBER", ErrorKind.RULE_VIOLATION, "이 가족의 구성원이 아닙니다: profile=$profileId")

class NotParticipantException(
    missionId: UUID,
    profileId: UUID,
) : DomainException("NOT_PARTICIPANT", ErrorKind.RULE_VIOLATION, "미션 참여자가 아닙니다: mission=$missionId profile=$profileId")

class InvalidMetricException(
    expected: TargetMetric,
    actual: TargetMetric,
) : DomainException("INVALID_METRIC", ErrorKind.RULE_VIOLATION, "$expected 미션이 아닙니다 (실제: $actual)")

class TargetNotReachedException(
    progress: Double,
) : DomainException("TARGET_NOT_REACHED", ErrorKind.RULE_VIOLATION, "아직 목표에 도달하지 않았습니다 (진행도 $progress)")

class CoachRunNotFoundException(
    runId: UUID,
) : DomainException("COACH_RUN_NOT_FOUND", ErrorKind.NOT_FOUND, "코치 실행이 없습니다: $runId")

class MissionNotFoundException(
    missionId: UUID,
) : DomainException("MISSION_NOT_FOUND", ErrorKind.NOT_FOUND, "미션이 없습니다: $missionId")

class VideoNotFoundException(
    videoId: String,
) : DomainException("VIDEO_NOT_FOUND", ErrorKind.NOT_FOUND, "영상이 없습니다: $videoId")

class ConversationNotFoundException(
    conversationId: UUID,
) : DomainException("CONVERSATION_NOT_FOUND", ErrorKind.NOT_FOUND, "대화가 없습니다: $conversationId")

class CoachingForbiddenException(
    message: String,
) : DomainException("FORBIDDEN", ErrorKind.FORBIDDEN, message)
