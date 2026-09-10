package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import kr.ac.kookmin.familyfitness.shared.ai.Citation
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Band
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

/**
 * AI 서비스가 죽었을 때의 대체 편성(보드 F3 「LLM 없이도 돈다」).
 * 임베딩·LLM 없이 영상 라벨(연령·요인)과 국민체력100 규준 백분위만으로 한 주 미션을 만든다.
 * 결과는 AI 응답과 같은 모양([CoachRunResult])이라 저장·승인 경로가 갈라지지 않는다.
 * 근거 없이는 미션을 내지 않는다 — 인용은 영상 라벨 또는 규준 백분위로 항상 1개 이상이다.
 * 시연 중 외부 장애로 빈 화면이 뜨는 것을 막는 안전망이며, AI 편성을 대신하는 것이 아니다.
 */
@Component
class LabelBasedProposalPlanner(
    private val profileQuery: ProfileQuery,
    private val fitnessQuery: FitnessQuery,
    private val videos: ExerciseVideoRepository,
) {
    /** 측정된 구성원이 없으면 null (제안을 만들 근거가 없다). */
    fun plan(
        familyId: UUID,
        weekStart: LocalDate,
        daysPerWeek: Int,
        minutesPerSession: Int,
        today: LocalDate,
        failureSummary: String,
    ): CoachRunResult? {
        val members = profileQuery.detailsOfFamily(familyId)
        val measured = members.mapNotNull { m -> fitnessQuery.latestOf(m.profileId)?.let { m to it } }
        val (driver, latest) =
            measured
                .sortedWith(compareBy<Pair<ProfileDetails, LatestFitness>> { if (it.first.role == ProfileRole.CHILD) 0 else 1 })
                .firstOrNull() ?: return null

        val target = latest.weakest?.takeIf { it.percentile <= Band.STRENGTH_FROM } ?: latest.strongest ?: latest.weakest ?: return null
        val direction = if (latest.weakest != null && target === latest.weakest) "키우기" else "강점 강화"
        val factor: FitnessFactor = target.factor
        val ageGroup = AgeGroup.of(driver.birthDate, today)
        val video =
            videos
                .findAllAfter(null)
                .filter { it.label.suitableFor(ageGroup) && it.label.hasFactor(factor.label) }
                // 연령 범위가 좁은(그 연령대를 겨냥한) 영상을 먼저, 같으면 짧은 것
                .sortedWith(compareBy({ (it.label.ageTo ?: 120) - (it.label.ageFrom ?: 0) }, { it.durationSec ?: Int.MAX_VALUE }))
                .firstOrNull()

        val citations =
            buildList {
                add(
                    Citation(
                        index = 1,
                        label = "국민체력100 규준 · ${ageGroup.label} ${factor.label} 백분위 ${target.percentile}",
                        chunkId = "norm:${ageGroup.label}-${target.itemCode}",
                        url = null,
                    ),
                )
                if (video != null) {
                    add(Citation(index = 2, label = "국민체력100 운동영상 · ${video.title}", chunkId = "video:${video.videoId}", url = video.url))
                }
            }
        // 응원만 하는 부모는 참여자로 자동 배정하지 않는다(보드 v2 원칙 · AI 계약 §5.1)
        val participants =
            members
                .map { CoachRunResult.ParticipantRef(ProfileRef.of(it.profileId), CoachRoles.of(it.role, it.supportMode)) }
                .filter { it.role != CoachRoles.CHEER }
                .sortedBy { if (it.ref == ProfileRef.of(driver.profileId)) 0 else 1 }
        val sessions =
            (0 until daysPerWeek).map { i ->
                CoachRunResult.Session(
                    dayOffset = (i * 7) / daysPerWeek,
                    exerciseName = video?.title ?: "${factor.label} 운동",
                    fitnessFactor = factor.label,
                    durationMin = minutesPerSession,
                    video = video?.let { CoachRunResult.Video(it.videoId, null) },
                    evidence = citations.map { it.index },
                )
            }
        val bandCopy = Band.ofPercentile(target.percentile).copy
        val mission =
            CoachRunResult.Mission(
                title = "${factor.label} $direction · 한 주",
                startDate = weekStart.toString(),
                endDate = weekStart.plusDays(6).toString(),
                participants = participants,
                sessions = sessions,
                copyChild = "이번 주는 ${factor.label}을 키우는 동작을 가족과 함께 해볼까요",
                copyParent = "${factor.label}은 $bandCopy 입니다. 주 ${daysPerWeek}회 ${minutesPerSession}분이면 충분합니다",
            )
        return CoachRunResult(
            runId = "fallback:${UUID.randomUUID()}",
            status = "succeeded",
            steps =
                listOf(
                    CoachRunResult.Step(1, "assess", "ok", "가족 ${members.size}명 중 측정값 있는 구성원 ${measured.size}명 · 대상 요인 = ${factor.label}"),
                    CoachRunResult.Step(
                        2,
                        "retrieve",
                        "partial",
                        "AI 서비스 장애($failureSummary) → 영상 라벨 기반 편성 · 영상 ${if (video == null) 0 else 1}편",
                    ),
                    CoachRunResult.Step(3, "compose", "ok", "주 ${daysPerWeek}회 ${minutesPerSession}분 · 세션 ${sessions.size}건"),
                    CoachRunResult.Step(4, "verify", "ok", "인용 ${citations.size}건 · 연령 필터 확인"),
                ),
            proposal = CoachRunResult.Proposal(listOf(mission), citations),
            refused = false,
            refusalReason = null,
        )
    }
}
