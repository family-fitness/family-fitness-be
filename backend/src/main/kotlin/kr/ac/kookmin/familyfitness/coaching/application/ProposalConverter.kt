package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunRequest
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Ages
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** AI 로 보낼 프로필. 이름·생년월일·계정 식별자는 싣지 않고 [ProfileRef] 로만 가리킨다. */
object AiProfileFactory {
    private val NOT_ALLOWED_ITEMS = setOf("005", "006")

    fun of(
        details: ProfileDetails,
        measurements: Map<String, BigDecimal>,
        heightCm: BigDecimal?,
        weightKg: BigDecimal?,
        on: LocalDate,
    ): AiProfile {
        val ageGroup = AgeGroup.of(details.birthDate, on)
        return AiProfile(
            profileRef = ProfileRef.of(details.profileId),
            age = if (ageGroup == AgeGroup.TODDLER) Ages.fullMonths(details.birthDate, on) else Ages.fullYears(details.birthDate, on),
            ageUnit = ageGroup.ageUnit,
            sex = details.sex.name,
            heightCm = (heightCm ?: details.heightCm)?.toDouble(),
            weightKg = (weightKg ?: details.weightKg)?.toDouble(),
            measurements = measurements.filterKeys { it !in NOT_ALLOWED_ITEMS }.mapValues { it.value.toDouble() },
        )
    }

    fun participant(
        details: ProfileDetails,
        profile: AiProfile,
    ): CoachRunRequest.Participant = CoachRunRequest.Participant(profile, CoachRoles.of(details.role, details.supportMode))
}

/**
 * AI proposal → 제안 항목 변환(계약 §5).
 * missions[i] → position=i, title, rationale=copy.parent, targetMetric=TIMER_MINUTES, targetValue=Σduration_min,
 * video=첫 video≠null 세션(카탈로그에 있는 것만), participants=ref→profileId 역매핑, citations=evidence 가 가리키는 것(없으면 전체).
 */
class ProposalConverter(
    private val refIndex: Map<String, UUID>,
    private val roles: Map<UUID, ProfileRole>,
    private val coachRoles: Map<UUID, String>,
    private val knownVideoIds: Set<String>,
) {
    fun convert(proposal: CoachRunResult.Proposal): List<CoachProposalItem> =
        proposal.missions.mapIndexed { index, mission ->
            val sessions = mission.sessions
            val evidence = sessions.flatMap { it.evidence }.toSet()
            val citations =
                proposal.citations
                    .filter { evidence.isEmpty() || it.index in evidence }
                    .map { ProposalCitation(it.index, it.label, it.chunkId, it.url) }
            val video =
                sessions
                    .firstNotNullOfOrNull { it.video }
                    ?.takeIf { it.videoId in knownVideoIds }
                    ?.let { ProposalVideo(it.videoId, it.startSec) }
            val participants =
                mission.participants.mapNotNull { p ->
                    val profileId = refIndex[p.ref] ?: return@mapNotNull null
                    ProposalParticipant(
                        profileId = profileId,
                        role = roles[profileId] ?: ProfileRole.CHILD,
                        coachRole = p.role.ifBlank { coachRoles[profileId] ?: CoachRoles.DRIVER },
                    )
                }
            CoachProposalItem(
                position = index,
                title = mission.title.take(MAX_TITLE),
                targetMetric = TargetMetric.TIMER_MINUTES.name,
                targetValue = sessions.sumOf { it.durationMin }.coerceAtLeast(1),
                rationale = mission.copyParent.take(MAX_COPY),
                description = sessions.joinToString(" · ") { "D+${it.dayOffset} ${it.exerciseName} ${it.durationMin}분" }.take(MAX_COPY),
                startsOn = LocalDate.parse(mission.startDate),
                endsOn = LocalDate.parse(mission.endDate),
                participants = participants,
                video = video,
                citations = citations,
                copyChild = mission.copyChild.take(MAX_COPY),
                copyParent = mission.copyParent.take(MAX_COPY),
            )
        }

    companion object {
        const val MAX_TITLE = 120
        const val MAX_COPY = 400

        fun steps(result: CoachRunResult): List<CoachStep> = result.steps.map { CoachStep(it.seq, it.name, it.status, it.summary) }

        /** 요약 = 첫 미션의 부모용 문구, 없으면 단계 요약을 이어 붙인 것. */
        fun summary(result: CoachRunResult): String? =
            result.proposal
                ?.missions
                ?.firstOrNull()
                ?.copyParent
                ?.takeIf { it.isNotBlank() }
                ?: result.steps.joinToString(" · ") { it.summary }.takeIf { it.isNotBlank() }
    }
}
