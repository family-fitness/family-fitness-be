package kr.ac.kookmin.familyfitness.shared.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * AI 서비스(FastAPI) 와이어 형식. 필드명은 계약 §5 의 snake_case 그대로다.
 * 도메인 DTO([AiGateway] 의 것)와는 [HttpAiGateway] 안에서만 오간다.
 */
internal object AiWire {
    data class ErrorEnvelope(
        val error: ErrorBody?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ErrorBody(
            val code: String?,
            val message: String?,
        )
    }

    data class ProfileBody(
        @JsonProperty("profile_ref") val profileRef: String,
        val age: Int,
        @JsonProperty("age_unit") val ageUnit: String,
        val sex: String,
        @JsonProperty("height_cm") val heightCm: Double?,
        @JsonProperty("weight_kg") val weightKg: Double?,
        val measurements: Map<String, Double>,
    ) {
        companion object {
            fun of(p: AiProfile) = ProfileBody(p.profileRef, p.age, p.ageUnit, p.sex, p.heightCm, p.weightKg, p.measurements)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CitationBody(
        val index: Int,
        val label: String,
        @JsonProperty("chunk_id") val chunkId: String,
        val url: String? = null,
    ) {
        fun toDomain() = Citation(index, label, chunkId, url)
    }

    // ---- assessment ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AssessmentBody(
        @JsonProperty("input_level") val inputLevel: String,
        @JsonProperty("age_group") val ageGroup: String,
        @JsonProperty("child_scope") val childScope: ChildScope?,
        @JsonProperty("parent_scope") val parentScope: ParentScope?,
        @JsonProperty("low_sample") val lowSample: Boolean = false,
        val disclaimer: String = "",
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ChildScope(
            @JsonProperty("focus_one") val focusOne: FocusOne?,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class FocusOne(
            val factor: String,
            val copy: String = "",
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ParentScope(
            val grade: String?,
            @JsonProperty("peer_distribution") val peerDistribution: List<GradeRatio> = emptyList(),
            val factors: List<FactorScore> = emptyList(),
            val copy: Map<String, String> = emptyMap(),
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class GradeRatio(
            val grade: String,
            val ratio: Double,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class FactorScore(
            val factor: String,
            @JsonProperty("item_code") val itemCode: String,
            @JsonProperty("item_name") val itemName: String = "",
            @JsonProperty("item_label") val itemLabel: String = "",
            val unit: String = "",
            val value: Double?,
            val score: Double?,
            val percentile: Int?,
            val band: String?,
            val n: Int = 0,
        )

        fun toDomain() =
            AssessmentResponse(
                inputLevel = inputLevel,
                ageGroup = ageGroup,
                childScope = AssessmentResponse.ChildScope(childScope?.focusOne?.let { AssessmentResponse.FocusOne(it.factor, it.copy) }),
                parentScope =
                    AssessmentResponse.ParentScope(
                        grade = parentScope?.grade,
                        peerDistribution =
                            parentScope?.peerDistribution.orEmpty().map {
                                AssessmentResponse.GradeRatio(
                                    it.grade,
                                    it.ratio,
                                )
                            },
                        factors =
                            parentScope?.factors.orEmpty().map {
                                AssessmentResponse.FactorScore(
                                    it.factor,
                                    it.itemCode,
                                    it.itemName,
                                    it.itemLabel,
                                    it.unit,
                                    it.value,
                                    it.score,
                                    it.percentile,
                                    it.band,
                                    it.n,
                                )
                            },
                        copy = parentScope?.copy.orEmpty(),
                    ),
                lowSample = lowSample,
                disclaimer = disclaimer,
            )
    }

    // ---- trajectory ----

    data class TrajectoryRequestBody(
        @JsonProperty("profile_ref") val profileRef: String,
        val age: Int,
        @JsonProperty("age_unit") val ageUnit: String,
        val sex: String,
        @JsonProperty("height_cm") val heightCm: Double?,
        @JsonProperty("weight_kg") val weightKg: Double?,
        val measurements: Map<String, Double>,
        @JsonProperty("item_code") val itemCode: String,
        @JsonProperty("horizon_years") val horizonYears: Int,
    ) {
        companion object {
            fun of(r: TrajectoryRequest) =
                r.profile.let {
                    TrajectoryRequestBody(
                        it.profileRef,
                        it.age,
                        it.ageUnit,
                        it.sex,
                        it.heightCm,
                        it.weightKg,
                        it.measurements,
                        r.itemCode,
                        r.horizonYears,
                    )
                }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TrajectoryBody(
        val basis: String,
        @JsonProperty("item_code") val itemCode: String,
        @JsonProperty("item_name") val itemName: String = "",
        val unit: String = "",
        val bands: List<BandBody> = emptyList(),
        val notice: String = "",
        @JsonProperty("low_sample") val lowSample: Boolean = false,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class BandBody(
            val age: Int,
            val p10: Double?,
            val p50: Double?,
            val p90: Double?,
            val n: Int = 0,
        )

        fun toDomain() =
            TrajectoryResponse(
                basis,
                itemCode,
                itemName,
                unit,
                bands.map {
                    TrajectoryResponse.Band(it.age, it.p10, it.p50, it.p90, it.n)
                },
                notice,
                lowSample,
            )
    }

    // ---- videos/search ----

    data class VideoSearchRequestBody(
        @JsonProperty("age_group") val ageGroup: String,
        @JsonProperty("fitness_factors") val fitnessFactors: List<String>,
        @JsonProperty("exercise_names") val exerciseNames: List<String>,
        val k: Int,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VideoSearchBody(
        val hits: List<HitBody> = emptyList(),
        @JsonProperty("filtered_out") val filteredOut: FilteredOut? = null,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class HitBody(
            @JsonProperty("video_id") val videoId: String,
            @JsonProperty("start_sec") val startSec: Int?,
            val score: Double = 0.0,
            @JsonProperty("matched_exercise_names") val matchedExerciseNames: List<String> = emptyList(),
            val citation: CitationBody,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class FilteredOut(
            @JsonProperty("age_group") val ageGroup: Int = 0,
            @JsonProperty("below_threshold") val belowThreshold: Int = 0,
        )

        fun toDomain() =
            VideoSearchResponse(
                hits =
                    hits.map {
                        VideoSearchResponse.Hit(
                            it.videoId,
                            it.startSec,
                            it.score,
                            it.matchedExerciseNames,
                            it.citation.toDomain(),
                        )
                    },
                filteredOutAgeGroup = filteredOut?.ageGroup ?: 0,
                filteredOutBelowThreshold = filteredOut?.belowThreshold ?: 0,
            )
    }

    // ---- coach/runs ----

    data class CoachRunRequestBody(
        @JsonProperty("profile_refs") val profileRefs: List<ProfileRefBody>,
        val period: Period,
        val constraints: Constraints,
    ) {
        data class ProfileRefBody(
            val ref: String,
            val role: String,
            val age: Int,
            @JsonProperty("age_unit") val ageUnit: String,
            val sex: String,
            @JsonProperty("input_level") val inputLevel: String,
            @JsonProperty("height_cm") val heightCm: Double?,
            @JsonProperty("weight_kg") val weightKg: Double?,
            val measurements: Map<String, Double>?,
        )

        data class Period(
            @JsonProperty("start_date") val startDate: String,
            val weeks: Int,
        )

        data class Constraints(
            @JsonProperty("days_per_week") val daysPerWeek: Int,
            @JsonProperty("minutes_per_session") val minutesPerSession: Int,
        )

        companion object {
            fun of(r: CoachRunRequest) =
                CoachRunRequestBody(
                    profileRefs =
                        r.profiles.map { p ->
                            val a = p.profile
                            ProfileRefBody(
                                ref = a.profileRef,
                                role = p.role,
                                age = a.age,
                                ageUnit = a.ageUnit,
                                sex = a.sex,
                                inputLevel = a.inputLevel,
                                heightCm = a.heightCm,
                                weightKg = a.weightKg,
                                measurements = a.measurements.takeIf { it.isNotEmpty() },
                            )
                        },
                    period = Period(r.startDate, r.weeks),
                    constraints = Constraints(r.daysPerWeek, r.minutesPerSession),
                )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CoachRunAcceptedBody(
        @JsonProperty("run_id") val runId: String,
        val status: String = "running",
        @JsonProperty("poll_after_ms") val pollAfterMs: Int = 1500,
    ) {
        fun toDomain() = CoachRunAccepted(runId, status, pollAfterMs)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CoachRunResultBody(
        @JsonProperty("run_id") val runId: String,
        val status: String,
        val steps: List<StepBody> = emptyList(),
        val proposal: ProposalBody? = null,
        val refused: Boolean = false,
        @JsonProperty("refusal_reason") val refusalReason: String? = null,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class StepBody(
            val seq: Int,
            val name: String,
            val status: String,
            val summary: String = "",
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ProposalBody(
            val missions: List<MissionBody> = emptyList(),
            val citations: List<CitationBody> = emptyList(),
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class MissionBody(
            val title: String,
            val period: PeriodBody,
            val participants: List<ParticipantBody> = emptyList(),
            val sessions: List<SessionBody> = emptyList(),
            val copy: CopyBody? = null,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class PeriodBody(
            @JsonProperty("start_date") val startDate: String,
            @JsonProperty("end_date") val endDate: String,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ParticipantBody(
            val ref: String,
            val role: String = "",
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class SessionBody(
            @JsonProperty("day_offset") val dayOffset: Int,
            @JsonProperty("exercise_name") val exerciseName: String = "",
            @JsonProperty("fitness_factor") val fitnessFactor: String = "",
            @JsonProperty("duration_min") val durationMin: Int = 0,
            val video: VideoBody? = null,
            val evidence: List<Int> = emptyList(),
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class VideoBody(
            @JsonProperty("video_id") val videoId: String,
            @JsonProperty("start_sec") val startSec: Int? = null,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class CopyBody(
            val child: String = "",
            val parent: String = "",
        )

        fun toDomain() =
            CoachRunResult(
                runId = runId,
                status = status,
                steps = steps.map { CoachRunResult.Step(it.seq, it.name, it.status, it.summary) },
                proposal =
                    proposal?.let { p ->
                        CoachRunResult.Proposal(
                            missions =
                                p.missions.map { m ->
                                    CoachRunResult.Mission(
                                        title = m.title,
                                        startDate = m.period.startDate,
                                        endDate = m.period.endDate,
                                        participants = m.participants.map { CoachRunResult.ParticipantRef(it.ref, it.role) },
                                        sessions =
                                            m.sessions.map { s ->
                                                CoachRunResult.Session(
                                                    dayOffset = s.dayOffset,
                                                    exerciseName = s.exerciseName,
                                                    fitnessFactor = s.fitnessFactor,
                                                    durationMin = s.durationMin,
                                                    video = s.video?.let { CoachRunResult.Video(it.videoId, it.startSec) },
                                                    evidence = s.evidence,
                                                )
                                            },
                                        copyChild = m.copy?.child ?: "",
                                        copyParent = m.copy?.parent ?: "",
                                    )
                                },
                            citations = p.citations.map { it.toDomain() },
                        )
                    },
                refused = refused,
                refusalReason = refusalReason,
            )
    }

    // ---- coach/messages ----

    data class CoachMessageRequestBody(
        @JsonProperty("profile_ref") val profileRef: String,
        @JsonProperty("age_group") val ageGroup: String,
        val question: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CoachMessageBody(
        val answer: String = "",
        val citations: List<CitationBody> = emptyList(),
        val refused: Boolean = false,
        @JsonProperty("refusal_reason") val refusalReason: String? = null,
    ) {
        fun toDomain() = CoachMessageResponse(answer, citations.map { it.toDomain() }, refused, refusalReason)
    }
}
