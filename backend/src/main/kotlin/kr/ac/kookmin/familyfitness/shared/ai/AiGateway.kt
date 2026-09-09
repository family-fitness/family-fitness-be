package kr.ac.kookmin.familyfitness.shared.ai

import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind

/**
 * AI 서비스(FastAPI, `{base-url}/v1`) 호출 계약. Notion 「인터페이스 명세」(docs/03) 의 필드를 그대로 옮겼다.
 * AI 는 서비스 테이블에 쓰지 않고 JSON 만 돌려준다. 저장·승인은 전부 이쪽(API 서버)이 한다.
 * 구현: `HttpAiGateway`(app.ai.mode=http) · `StubAiGateway`(app.ai.mode=stub, AI 서비스 없이 결정적 응답).
 */
interface AiGateway {
    /** POST /v1/fitness/assessment — 3s · 재시도 2회 */
    fun assess(request: AssessmentRequest): AssessmentResponse

    /** POST /v1/fitness/trajectory — 3s · 재시도 2회 */
    fun trajectory(request: TrajectoryRequest): TrajectoryResponse

    /** POST /v1/videos/search — 4s · 재시도 2회 */
    fun searchVideos(request: VideoSearchRequest): VideoSearchResponse

    /** POST /v1/coach/runs — 2s · 재시도 0회. 202 접수. 중복은 409 [AiRunInProgressException]. */
    fun startCoachRun(request: CoachRunRequest): CoachRunAccepted

    /** GET /v1/coach/runs/{run_id} — 3s. 호출자가 1.5s 간격 최대 40회 폴링한다. */
    fun getCoachRun(runId: String): CoachRunResult

    /** POST /v1/coach/messages — 10s · 재시도 0회(중복 과금). */
    fun ask(request: CoachMessageRequest): CoachMessageResponse
}

/** LLM·인덱스 일시 장애, 타임아웃, 연결 실패 → 503. */
class AiUnavailableException(
    message: String,
    cause: Throwable? = null,
) : DomainException("TEMPORARILY_UNAVAILABLE", ErrorKind.UNAVAILABLE, message, cause)

class AiRunInProgressException(
    message: String = "실행 중인 코치 실행이 있습니다",
) : DomainException("RUN_IN_PROGRESS", ErrorKind.CONFLICT, message)

class AiRunNotFoundException(
    runId: String,
) : DomainException("RUN_NOT_FOUND", ErrorKind.NOT_FOUND, "AI run 이 없습니다: $runId")

/** AI 가 400 을 돌려준 것은 호출자(이쪽) 버그다. 화면에 띄우지 않고 로그로 남긴다. */
class AiBadRequestException(
    message: String,
) : DomainException("AI_BAD_REQUEST", ErrorKind.UNAVAILABLE, message)

// ---- 공통 ----

/** 요청 프로필. 이름·생년월일·계정 식별자를 담지 않는다. */
data class AiProfile(
    val profileRef: String,
    /** 유아기는 개월, 그 외는 세 */
    val age: Int,
    val ageUnit: String,
    val sex: String,
    val heightCm: Double?,
    val weightKg: Double?,
    /** itemCode → 값. 005·006 제외. */
    val measurements: Map<String, Double>,
) {
    val inputLevel: String
        get() =
            when {
                measurements.isNotEmpty() -> "L2"
                heightCm != null && weightKg != null -> "L1"
                else -> "L0"
            }
}

data class Citation(
    val index: Int,
    val label: String,
    val chunkId: String,
    val url: String?,
)

// ---- assessment ----

data class AssessmentRequest(
    val profile: AiProfile,
)

data class AssessmentResponse(
    val inputLevel: String,
    val ageGroup: String,
    val childScope: ChildScope,
    val parentScope: ParentScope,
    val lowSample: Boolean,
    val disclaimer: String,
) {
    data class ChildScope(
        val focusOne: FocusOne?,
    )

    data class FocusOne(
        val factor: String,
        val copy: String,
    )

    data class ParentScope(
        val grade: String?,
        val peerDistribution: List<GradeRatio>,
        val factors: List<FactorScore>,
        val copy: Map<String, String>,
    )

    data class GradeRatio(
        val grade: String,
        val ratio: Double,
    )

    data class FactorScore(
        val factor: String,
        val itemCode: String,
        val itemName: String,
        val itemLabel: String,
        val unit: String,
        val value: Double?,
        val score: Double?,
        val percentile: Int?,
        val band: String?,
        val n: Int,
    )
}

// ---- trajectory ----

data class TrajectoryRequest(
    val profile: AiProfile,
    val itemCode: String = "028",
    val horizonYears: Int = 10,
)

data class TrajectoryResponse(
    val basis: String,
    val itemCode: String,
    val itemName: String,
    val unit: String,
    val bands: List<Band>,
    val notice: String,
    val lowSample: Boolean,
) {
    data class Band(
        val age: Int,
        val p10: Double?,
        val p50: Double?,
        val p90: Double?,
        val n: Int,
    )
}

// ---- videos/search ----

data class VideoSearchRequest(
    val ageGroup: String,
    val fitnessFactors: List<String> = emptyList(),
    val exerciseNames: List<String> = emptyList(),
    val k: Int = 5,
)

data class VideoSearchResponse(
    val hits: List<Hit>,
    val filteredOutAgeGroup: Int,
    val filteredOutBelowThreshold: Int,
) {
    data class Hit(
        val videoId: String,
        val startSec: Int?,
        val score: Double,
        val matchedExerciseNames: List<String>,
        val citation: Citation,
    )
}

// ---- coach/runs ----

data class CoachRunRequest(
    val profiles: List<Participant>,
    val startDate: String,
    val weeks: Int = 1,
    val daysPerWeek: Int,
    val minutesPerSession: Int,
) {
    /** role: 주행자 · 동반자 · 응원 (응원은 편성 대상에서 빠지고 참여자로만 기록) */
    data class Participant(
        val profile: AiProfile,
        val role: String,
    )
}

data class CoachRunAccepted(
    val runId: String,
    val status: String,
    val pollAfterMs: Int,
)

data class CoachRunResult(
    val runId: String,
    /** running · succeeded · failed · refused */
    val status: String,
    val steps: List<Step>,
    val proposal: Proposal?,
    val refused: Boolean,
    val refusalReason: String?,
) {
    val isRunning: Boolean get() = status == "running"

    data class Step(
        val seq: Int,
        val name: String,
        val status: String,
        val summary: String,
    )

    data class Proposal(
        val missions: List<Mission>,
        val citations: List<Citation>,
    )

    data class Mission(
        val title: String,
        val startDate: String,
        val endDate: String,
        val participants: List<ParticipantRef>,
        val sessions: List<Session>,
        val copyChild: String,
        val copyParent: String,
    )

    data class ParticipantRef(
        val ref: String,
        val role: String,
    )

    data class Session(
        val dayOffset: Int,
        val exerciseName: String,
        val fitnessFactor: String,
        val durationMin: Int,
        val video: Video?,
        val evidence: List<Int>,
    )

    data class Video(
        val videoId: String,
        val startSec: Int?,
    )
}

// ---- coach/messages ----

data class CoachMessageRequest(
    val profileRef: String,
    val ageGroup: String,
    val question: String,
)

data class CoachMessageResponse(
    val answer: String,
    val citations: List<Citation>,
    val refused: Boolean,
    /** no_relevant_source · age_filter_empty · medical_query · no_citation_generated */
    val refusalReason: String?,
)
