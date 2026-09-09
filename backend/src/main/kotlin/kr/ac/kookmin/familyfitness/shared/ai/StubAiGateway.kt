package kr.ac.kookmin.familyfitness.shared.ai

import kr.ac.kookmin.familyfitness.shared.domain.Copy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * **로컬·데모 전용** [AiGateway]. AI 서비스(`family-fitness-ai`)에 `/v1` 경로가 아직 없어(AI-4 이후) 결정적인 가짜 응답을 돌려준다.
 * 운영에서는 `app.ai.mode=http` 로 [HttpAiGateway] 를 쓴다. 여기 값은 계약 §5 의 모양만 맞춘 예시이며 어떤 근거도 없다.
 */
@Component
@ConditionalOnProperty(name = ["app.ai.mode"], havingValue = "stub", matchIfMissing = true)
class StubAiGateway : AiGateway {
    private val runs = ConcurrentHashMap<String, CoachRunRequest>()

    override fun assess(request: AssessmentRequest): AssessmentResponse {
        val p = request.profile
        return AssessmentResponse(
            inputLevel = p.inputLevel,
            ageGroup = ageGroupOf(p),
            childScope = AssessmentResponse.ChildScope(AssessmentResponse.FocusOne("유연성", "지금 키우기 좋은 영역")),
            parentScope =
                AssessmentResponse.ParentScope(
                    grade = if (p.measurements.isEmpty()) null else "3등급",
                    peerDistribution =
                        listOf(
                            AssessmentResponse.GradeRatio("1등급", 0.10),
                            AssessmentResponse.GradeRatio("2등급", 0.15),
                            AssessmentResponse.GradeRatio("3등급", 0.25),
                            AssessmentResponse.GradeRatio("참가", 0.50),
                        ),
                    factors =
                        p.measurements.map { (code, value) ->
                            AssessmentResponse.FactorScore(
                                factor = FACTOR_OF[code] ?: "근력",
                                itemCode = code,
                                itemName = ITEM_NAME[code] ?: code,
                                itemLabel = ITEM_NAME[code] ?: code,
                                unit = UNIT_OF[code] ?: "",
                                value = value,
                                score = 50.0,
                                percentile = 50,
                                band = "steady",
                                n = 120,
                            )
                        },
                    copy = mapOf("strength" to "꾸준히 하고 있는 영역이 많습니다.", "focus" to "유연성은 지금 키우기 좋은 영역입니다."),
                ),
            lowSample = false,
            disclaimer = Copy.FITNESS_DISCLAIMER,
        )
    }

    override fun trajectory(request: TrajectoryRequest): TrajectoryResponse {
        val p = request.profile
        val ageYears = if (p.ageUnit == "개월") p.age / 12 else p.age
        val base = p.measurements[request.itemCode] ?: 50.0
        val bands =
            listOf(0, 4, 8, 10)
                .filter { it <= request.horizonYears }
                .map { offset ->
                    val p50 = round1(base * (1 + 0.02 * offset))
                    TrajectoryResponse.Band(age = ageYears + offset, p10 = round1(p50 * 0.8), p50 = p50, p90 = round1(p50 * 1.2), n = 120)
                }
        return TrajectoryResponse(
            basis = "cross_sectional_group_distribution",
            itemCode = request.itemCode,
            itemName = ITEM_NAME[request.itemCode] ?: request.itemCode,
            unit = UNIT_OF[request.itemCode] ?: "",
            bands = bands,
            notice = Copy.TRAJECTORY_NOTICE,
            lowSample = false,
        )
    }

    override fun searchVideos(request: VideoSearchRequest): VideoSearchResponse =
        VideoSearchResponse(
            hits =
                listOf(
                    VideoSearchResponse.Hit(
                        videoId = SAMPLE_VIDEO,
                        startSec = SAMPLE_VIDEO_START,
                        score = 0.82,
                        matchedExerciseNames = request.exerciseNames.take(1).ifEmpty { listOf(EXERCISES.first()) },
                        citation = videoCitation(2),
                    ),
                ),
            filteredOutAgeGroup = 0,
            filteredOutBelowThreshold = 0,
        )

    override fun startCoachRun(request: CoachRunRequest): CoachRunAccepted {
        val runId = "cr_" + UUID.randomUUID().toString().replace("-", "")
        runs[runId] = request
        return CoachRunAccepted(runId, "running", 1500)
    }

    override fun getCoachRun(runId: String): CoachRunResult {
        val request = runs[runId] ?: throw AiRunNotFoundException(runId)
        val drivers = request.profiles.filter { it.role == ROLE_DRIVER }
        val companions = request.profiles.filter { it.role == ROLE_COMPANION }
        val subjects = drivers.ifEmpty { companions }
        val steps =
            listOf(
                CoachRunResult.Step(
                    1,
                    "assess",
                    "ok",
                    "가족 ${request.profiles.size}명 중 측정값 있는 구성원 ${request.profiles.count { it.profile.measurements.isNotEmpty() }}명",
                ),
                CoachRunResult.Step(2, "retrieve", "ok", "또래 운동처방 1건·영상 1편 검색"),
                CoachRunResult.Step(3, "compose", "ok", "주행자 ${drivers.size}명에게 유연성 미션 ${subjects.size}개 편성"),
                CoachRunResult.Step(4, "verify", "ok", "연령 필터·근거 인용 확인"),
            )
        if (subjects.isEmpty()) {
            return CoachRunResult(runId, "refused", steps, null, refused = true, refusalReason = "no_relevant_source")
        }
        val start = LocalDate.parse(request.startDate)
        val missions =
            subjects.map { subject ->
                CoachRunResult.Mission(
                    title = "같이 늘이는 한 주",
                    startDate = start.toString(),
                    endDate = start.plusDays(6).toString(),
                    participants =
                        (listOf(subject) + companions.filter { it !== subject })
                            .map { CoachRunResult.ParticipantRef(it.profile.profileRef, it.role) },
                    sessions =
                        SESSION_OFFSETS.mapIndexed { i, offset ->
                            CoachRunResult.Session(
                                dayOffset = offset,
                                exerciseName = EXERCISES[i],
                                fitnessFactor = "유연성",
                                durationMin = request.minutesPerSession,
                                video =
                                    if (i == 0 ||
                                        i == SESSION_OFFSETS.lastIndex
                                    ) {
                                        CoachRunResult.Video(SAMPLE_VIDEO, SAMPLE_VIDEO_START)
                                    } else {
                                        null
                                    },
                                evidence = listOf(1, 2),
                            )
                        },
                    copyChild = "이번 주엔 다리를 쭉 펴고 앞으로 천천히 숙여 보자!",
                    copyParent = "유연성은 매일 조금씩 늘려 가는 영역입니다. 한 주 ${SESSION_OFFSETS.size}회, 회당 ${request.minutesPerSession}분이면 충분합니다.",
                )
            }
        return CoachRunResult(
            runId = runId,
            status = "succeeded",
            steps = steps,
            proposal =
                CoachRunResult.Proposal(
                    missions = missions,
                    citations = listOf(Citation(1, "국민체력100 운동처방 · 유소년 11세", "prescription:유소년-11-F-0142", null), videoCitation(2)),
                ),
            refused = false,
            refusalReason = null,
        )
    }

    override fun ask(request: CoachMessageRequest): CoachMessageResponse {
        if (MEDICAL_WORDS.any { request.question.contains(it) }) {
            return CoachMessageResponse(answer = "", citations = emptyList(), refused = true, refusalReason = "medical_query")
        }
        return CoachMessageResponse(
            answer = "또래 처방에서는 상체를 앞·옆으로 숙이는 준비운동 동작이 함께 제시됩니다 [1].",
            citations = listOf(Citation(1, "국민체력100 운동처방 · 유소년 11세", "prescription:유소년-11-F-0142", null)),
            refused = false,
            refusalReason = null,
        )
    }

    private fun ageGroupOf(p: AiProfile): String {
        val years = if (p.ageUnit == "개월") p.age / 12 else p.age
        return when {
            years < 7 -> "유아기"
            years < 13 -> "유소년"
            years < 19 -> "청소년"
            years < 65 -> "성인"
            else -> "어르신"
        }
    }

    private fun videoCitation(index: Int) =
        Citation(
            index,
            "국민체력100 · 초등학생의 기초체력향상과 운동능력발달을 위한 운동",
            "video:$SAMPLE_VIDEO",
            "https://www.youtube.com/watch?v=$SAMPLE_VIDEO&t=${SAMPLE_VIDEO_START}s",
        )

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    companion object {
        const val ROLE_DRIVER = "주행자"
        const val ROLE_COMPANION = "동반자"
        const val SAMPLE_VIDEO = "IdpXx2gm90o"
        const val SAMPLE_VIDEO_START = 96
        val SESSION_OFFSETS = listOf(0, 2, 4)
        val EXERCISES = listOf("다리 벌려 앞으로 상체 숙이기", "앉아서 윗몸 앞으로 굽히기", "무릎 펴고 발끝 잡기")
        val MEDICAL_WORDS = listOf("통증", "부상", "약물", "질환", "아파", "다쳤")
        private val ITEM_NAME = mapOf("028" to "상대악력", "012" to "앉아윗몸앞으로굽히기", "009" to "윗몸말아올리기", "022" to "제자리멀리뛰기")
        private val UNIT_OF = mapOf("028" to "%", "012" to "cm", "009" to "회", "022" to "cm")
        private val FACTOR_OF = mapOf("028" to "근력", "012" to "유연성", "009" to "근지구력", "022" to "순발력")
    }
}
