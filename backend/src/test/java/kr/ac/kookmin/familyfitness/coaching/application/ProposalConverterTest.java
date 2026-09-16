package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProposalConverterTest {
    private final Family family = new Family();
    private final ProposalConverter converter = new ProposalConverter(
            ProfileRef.indexOf(
                    family.members().stream().map(ProfileDetails::profileId).toList()),
            roles(),
            Map.of(
                    family.child.profileId(), "주행자",
                    family.parent.profileId(), "동반자",
                    family.cheerParent.profileId(), "응원"),
            Set.of("IdpXx2gm90o"));

    private Map<UUID, ProfileRole> roles() {
        Map<UUID, ProfileRole> roles = new LinkedHashMap<>();
        family.members().forEach(it -> roles.put(it.profileId(), it.role()));
        return roles;
    }

    private static CoachRunResult.Session session(
            int offset, int minutes, CoachRunResult.@Nullable Video video, List<Integer> evidence) {
        return new CoachRunResult.Session(offset, "운동", "유연성", minutes, video, evidence);
    }

    private static CoachRunResult.Proposal proposal(List<CoachRunResult.Mission> missions) {
        return proposal(
                missions,
                List.of(
                        new Citation(1, "처방", "prescription:1", null),
                        new Citation(2, "영상", "video:IdpXx2gm90o", "https://y/1"),
                        new Citation(3, "기타", "x", null)));
    }

    private static CoachRunResult.Proposal proposal(List<CoachRunResult.Mission> missions, List<Citation> citations) {
        return new CoachRunResult.Proposal(missions, citations);
    }

    @Test
    @DisplayName("미션은 위치·제목·부모 문구·TIMER_MINUTES·분 합계·첫 영상·역매핑 참여자·evidence 인용으로 바뀐다")
    void 미션은_위치_제목_부모_문구_TIMER_MINUTES_분_합계_첫_영상_역매핑_참여자_evidence_인용으로_바뀐다() {
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                "같이 늘이는 한 주",
                "2026-09-07",
                "2026-09-13",
                List.of(
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자"),
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.parent.profileId()), "동반자"),
                        new CoachRunResult.ParticipantRef("p_unknown", "주행자")),
                List.of(
                        session(0, 15, null, List.of(1)),
                        session(2, 15, new CoachRunResult.Video("IdpXx2gm90o", 96), List.of(1, 2)),
                        session(4, 20, new CoachRunResult.Video("other", 0), List.of())),
                "아이 문구",
                "부모 문구");

        List<CoachProposalItem> items = converter.convert(proposal(List.of(mission)));
        assertThat(items).hasSize(1);
        CoachProposalItem item = items.getFirst();

        assertThat(item.position()).isEqualTo(0);
        assertThat(item.title()).isEqualTo("같이 늘이는 한 주");
        assertThat(item.rationale()).isEqualTo("부모 문구");
        assertThat(item.targetMetric()).isEqualTo("TIMER_MINUTES");
        assertThat(item.targetValue()).isEqualTo(50);
        assertThat(item.video()).isEqualTo(new ProposalVideo("IdpXx2gm90o", 96));
        assertThat(item.startsOn()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(item.endsOn()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(item.participants().stream()
                        .map(ProposalParticipant::profileId)
                        .toList())
                .containsExactly(family.child.profileId(), family.parent.profileId());
        assertThat(item.participants().stream().map(ProposalParticipant::role).toList())
                .containsExactly(ProfileRole.CHILD, ProfileRole.PARENT);
        assertThat(item.participants().stream()
                        .map(ProposalParticipant::coachRole)
                        .toList())
                .containsExactly("주행자", "동반자");
        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1, 2);
        assertThat(item.copyChild()).isEqualTo("아이 문구");
    }

    @Test
    @DisplayName("evidence 가 하나도 없으면 실행 전체 인용을 붙이고, 모르는 영상은 버린다")
    void evidence_가_하나도_없으면_실행_전체_인용을_붙이고_모르는_영상은_버린다() {
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                "t",
                "2026-09-07",
                "2026-09-13",
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "")),
                List.of(session(0, 0, new CoachRunResult.Video("unknown", null), List.of())),
                "",
                "");

        List<CoachProposalItem> items = converter.convert(proposal(List.of(mission)));
        assertThat(items).hasSize(1);
        CoachProposalItem item = items.getFirst();

        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1, 2, 3);
        assertThat(item.video()).isNull();
        assertThat(item.targetValue()).isEqualTo(1);
        assertThat(item.participants())
                .singleElement()
                .extracting(ProposalParticipant::coachRole)
                .isEqualTo("주행자");
    }

    @Test
    @DisplayName("AI 프로필은 이름 없이 ref·나이·성별·측정만 담고 유아기는 개월로 센다")
    void AI_프로필은_이름_없이_ref_나이_성별_측정만_담고_유아기는_개월로_센다() {
        ProfileDetails base = family.child;
        ProfileDetails toddler = new ProfileDetails(
                base.profileId(),
                base.familyId(),
                base.userId(),
                base.name(),
                base.role(),
                Fixed.TODAY.minusYears(3).minusMonths(2),
                base.sex(),
                base.heightCm(),
                base.weightKg(),
                base.supportMode(),
                base.consentGiven());
        AiProfile profile = AiProfileFactory.of(
                toddler, Map.of("012", new BigDecimal("5.5"), "005", new BigDecimal("80")), null, null, Fixed.TODAY);

        assertThat(profile.profileRef()).isEqualTo(ProfileRef.of(toddler.profileId()));
        assertThat(profile.age()).isEqualTo(38);
        assertThat(profile.ageUnit()).isEqualTo("개월");
        assertThat(profile.sex()).isEqualTo("M");
        assertThat(profile.measurements()).containsOnlyKeys("012");
        assertThat(profile.inputLevel()).isEqualTo("L2");

        AiProfile child =
                AiProfileFactory.of(family.child, Map.of(), new BigDecimal("140.5"), new BigDecimal("35"), Fixed.TODAY);
        assertThat(child.age()).isEqualTo(11);
        assertThat(child.ageUnit()).isEqualTo("세");
        assertThat(child.inputLevel()).isEqualTo("L1");
        assertThat(AiProfileFactory.participant(family.child, child).role()).isEqualTo("주행자");
        assertThat(AiProfileFactory.participant(family.parent, child).role()).isEqualTo("동반자");
        assertThat(AiProfileFactory.participant(family.cheerParent, child).role())
                .isEqualTo("응원");
    }

    @Test
    @DisplayName("요약은 첫 미션의 부모 문구, 없으면 단계 요약을 이어 붙인다")
    void 요약은_첫_미션의_부모_문구_없으면_단계_요약을_이어_붙인다() {
        List<CoachRunResult.Step> steps = List.of(
                new CoachRunResult.Step(1, "assess", "ok", "측정 2명"), new CoachRunResult.Step(2, "verify", "ok", "확인"));
        CoachRunResult withProposal = new CoachRunResult(
                "r",
                "succeeded",
                steps,
                proposal(List.of(new CoachRunResult.Mission(
                        "t", "2026-09-07", "2026-09-13", List.of(), List.of(), "", "부모 요약"))),
                false,
                null);
        assertThat(ProposalConverter.summary(withProposal)).isEqualTo("부모 요약");
        assertThat(ProposalConverter.summary(new CoachRunResult("r", "failed", steps, null, false, null)))
                .isEqualTo("측정 2명 · 확인");
        assertThat(ProposalConverter.steps(withProposal).stream()
                        .map(kr.ac.kookmin.familyfitness.coaching.domain.CoachStep::name)
                        .toList())
                .containsExactly("assess", "verify");
    }
}
