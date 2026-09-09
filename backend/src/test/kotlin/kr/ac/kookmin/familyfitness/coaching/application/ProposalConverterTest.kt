package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo
import kr.ac.kookmin.familyfitness.coaching.support.Family
import kr.ac.kookmin.familyfitness.coaching.support.Fixed
import kr.ac.kookmin.familyfitness.shared.ai.Citation
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ProposalConverterTest {
    private val family = Family()
    private val converter =
        ProposalConverter(
            refIndex = ProfileRef.indexOf(family.members.map { it.profileId }),
            roles = family.members.associate { it.profileId to it.role },
            coachRoles = mapOf(family.child.profileId to "주행자", family.parent.profileId to "동반자", family.cheerParent.profileId to "응원"),
            knownVideoIds = setOf("IdpXx2gm90o"),
        )

    private fun session(
        offset: Int,
        minutes: Int,
        video: CoachRunResult.Video?,
        evidence: List<Int>,
    ) = CoachRunResult.Session(offset, "운동", "유연성", minutes, video, evidence)

    private fun proposal(
        missions: List<CoachRunResult.Mission>,
        citations: List<Citation> =
            listOf(
                Citation(1, "처방", "prescription:1", null),
                Citation(2, "영상", "video:IdpXx2gm90o", "https://y/1"),
                Citation(3, "기타", "x", null),
            ),
    ) = CoachRunResult.Proposal(missions, citations)

    @Test
    fun `미션은 위치·제목·부모 문구·TIMER_MINUTES·분 합계·첫 영상·역매핑 참여자·evidence 인용으로 바뀐다`() {
        val mission =
            CoachRunResult.Mission(
                title = "같이 늘이는 한 주",
                startDate = "2026-09-07",
                endDate = "2026-09-13",
                participants =
                    listOf(
                        CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId), "주행자"),
                        CoachRunResult.ParticipantRef(ProfileRef.of(family.parent.profileId), "동반자"),
                        CoachRunResult.ParticipantRef("p_unknown", "주행자"),
                    ),
                sessions =
                    listOf(
                        session(0, 15, null, listOf(1)),
                        session(2, 15, CoachRunResult.Video("IdpXx2gm90o", 96), listOf(1, 2)),
                        session(4, 20, CoachRunResult.Video("other", 0), emptyList()),
                    ),
                copyChild = "아이 문구",
                copyParent = "부모 문구",
            )

        val item = converter.convert(proposal(listOf(mission))).single()

        assertThat(item.position).isEqualTo(0)
        assertThat(item.title).isEqualTo("같이 늘이는 한 주")
        assertThat(item.rationale).isEqualTo("부모 문구")
        assertThat(item.targetMetric).isEqualTo("TIMER_MINUTES")
        assertThat(item.targetValue).isEqualTo(50)
        assertThat(item.video).isEqualTo(ProposalVideo("IdpXx2gm90o", 96))
        assertThat(item.startsOn).isEqualTo(LocalDate.of(2026, 9, 7))
        assertThat(item.endsOn).isEqualTo(LocalDate.of(2026, 9, 13))
        assertThat(item.participants.map { it.profileId }).containsExactly(family.child.profileId, family.parent.profileId)
        assertThat(item.participants.map { it.role }).containsExactly(ProfileRole.CHILD, ProfileRole.PARENT)
        assertThat(item.participants.map { it.coachRole }).containsExactly("주행자", "동반자")
        assertThat(item.citations.map { it.index }).containsExactly(1, 2)
        assertThat(item.copyChild).isEqualTo("아이 문구")
    }

    @Test
    fun `evidence 가 하나도 없으면 실행 전체 인용을 붙이고, 모르는 영상은 버린다`() {
        val mission =
            CoachRunResult.Mission(
                title = "t",
                startDate = "2026-09-07",
                endDate = "2026-09-13",
                participants = listOf(CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId), "")),
                sessions = listOf(session(0, 0, CoachRunResult.Video("unknown", null), emptyList())),
                copyChild = "",
                copyParent = "",
            )

        val item = converter.convert(proposal(listOf(mission))).single()

        assertThat(item.citations.map { it.index }).containsExactly(1, 2, 3)
        assertThat(item.video).isNull()
        assertThat(item.targetValue).isEqualTo(1)
        assertThat(item.participants.single().coachRole).isEqualTo("주행자")
    }

    @Test
    fun `AI 프로필은 이름 없이 ref·나이·성별·측정만 담고 유아기는 개월로 센다`() {
        val toddler = family.child.copy(birthDate = Fixed.TODAY.minusYears(3).minusMonths(2))
        val profile = AiProfileFactory.of(toddler, mapOf("012" to BigDecimal("5.5"), "005" to BigDecimal("80")), null, null, Fixed.TODAY)

        assertThat(profile.profileRef).isEqualTo(ProfileRef.of(toddler.profileId))
        assertThat(profile.age).isEqualTo(38)
        assertThat(profile.ageUnit).isEqualTo("개월")
        assertThat(profile.sex).isEqualTo("M")
        assertThat(profile.measurements).containsOnlyKeys("012")
        assertThat(profile.inputLevel).isEqualTo("L2")

        val child = AiProfileFactory.of(family.child, emptyMap(), BigDecimal("140.5"), BigDecimal("35"), Fixed.TODAY)
        assertThat(child.age).isEqualTo(11)
        assertThat(child.ageUnit).isEqualTo("세")
        assertThat(child.inputLevel).isEqualTo("L1")
        assertThat(AiProfileFactory.participant(family.child, child).role).isEqualTo("주행자")
        assertThat(AiProfileFactory.participant(family.parent, child).role).isEqualTo("동반자")
        assertThat(AiProfileFactory.participant(family.cheerParent, child).role).isEqualTo("응원")
    }

    @Test
    fun `요약은 첫 미션의 부모 문구, 없으면 단계 요약을 이어 붙인다`() {
        val steps = listOf(CoachRunResult.Step(1, "assess", "ok", "측정 2명"), CoachRunResult.Step(2, "verify", "ok", "확인"))
        val withProposal =
            CoachRunResult(
                "r",
                "succeeded",
                steps,
                proposal(
                    listOf(
                        CoachRunResult.Mission("t", "2026-09-07", "2026-09-13", emptyList(), emptyList(), "", "부모 요약"),
                    ),
                ),
                false,
                null,
            )
        assertThat(ProposalConverter.summary(withProposal)).isEqualTo("부모 요약")
        assertThat(ProposalConverter.summary(CoachRunResult("r", "failed", steps, null, false, null))).isEqualTo("측정 2명 · 확인")
        assertThat(ProposalConverter.steps(withProposal).map { it.name }).containsExactly("assess", "verify")
    }
}
