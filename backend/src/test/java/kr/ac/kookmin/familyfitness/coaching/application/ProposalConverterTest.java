package kr.ac.kookmin.familyfitness.coaching.application;

import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.COOLDOWN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.MAIN;
import static kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase.WARMUP;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionClip;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.AiProfile;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProposalConverterTest {
    private final Family family = new Family();
    /** 대상 아이만, 보호자는 같이 하지 않는다. */
    private final ProposalConverter converter =
            new ProposalConverter(family.child.profileId(), ProfileRole.CHILD, null);

    private static CoachRunResult.Session session(
            @Nullable Integer order, CoachRunResult.@Nullable Video video, List<Integer> evidence) {
        return new CoachRunResult.Session(0, "본운동", order, "운동", "유연성", 40, video, evidence);
    }

    private static CoachRunResult.Mission mission(
            List<CoachRunResult.ParticipantRef> participants,
            @Nullable Integer durationMin,
            List<CoachRunResult.Session> sessions,
            String copyParent,
            String reason) {
        return new CoachRunResult.Mission(
                "일간",
                "t",
                "2026-09-07",
                "2026-09-07",
                participants,
                durationMin,
                null,
                sessions,
                "아이 문구",
                copyParent,
                reason);
    }

    private static CoachRunResult.Proposal proposal(List<CoachRunResult.Mission> missions) {
        return new CoachRunResult.Proposal(
                missions,
                List.of(
                        new Citation(1, "처방", "prescription:1", null),
                        new Citation(2, "영상", "video:IdpXx2gm90o", "https://y/1"),
                        new Citation(3, "기타", "x", null)),
                List.of());
    }

    private static CoachRunResult.Session clipSession(
            String phase, int order, String name, String factor, String videoId, int startSec, int endSec) {
        return new CoachRunResult.Session(
                0,
                phase,
                order,
                name,
                factor,
                endSec - startSec,
                new CoachRunResult.Video(videoId, startSec, endSec),
                List.of(1));
    }

    /**
     * ai:docs/인터페이스-명세.md 4장 응답 200 예시. 명세는 첫 칸만 적었으므로 나머지는 같은 영상의 실제 클립(V132)으로 채워
     * AI 가짓수 규칙의 15분 = 준비 2 · 본 4 · 정리 1 로 만들었다. ref 만 이 가족 아이로 바꿨다.
     */
    private CoachRunResult.Mission specExample() {
        return new CoachRunResult.Mission(
                "일간",
                "월요일 늘이기",
                "2026-09-07",
                "2026-09-07",
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자")),
                15,
                418,
                List.of(
                        clipSession("준비운동", 1, "넙다리 안쪽 늘리기 (나비자세)", "유연성", "Eg3GpTv7z8s", 144, 182),
                        clipSession("준비운동", 2, "척추 들어올리기 (고양이자세)", "유연성", "Eg3GpTv7z8s", 188, 226),
                        clipSession("본운동", 3, "앉아서 상체숙여 양팔 등 뒤로 펴기", "유연성", "Eg3GpTv7z8s", 500, 534),
                        clipSession("본운동", 4, "손 뒤에서 깍지 끼고 가슴펴기", "유연성", "Eg3GpTv7z8s", 536, 588),
                        clipSession("본운동", 5, "팔꿈치 등 뒤에서 굽히고 펴기", "근력", "Eg3GpTv7z8s", 614, 648),
                        clipSession("본운동", 6, "손목 잡고 팔 스트레칭", "유연성", "Eg3GpTv7z8s", 650, 724),
                        clipSession("정리운동", 7, "다리 뒤 늘리기", "유연성", "Eg3GpTv7z8s", 1426, 1466)),
                "이번 주는 몸을 길게 늘이는 동작, 엄마랑 같이 해볼까요",
                "유연성은 지금 키우기 좋은 영역입니다. 주 3회 15분이면 충분합니다",
                "또래 처방에 나온 늘이는 동작을 앞세워 골랐습니다 [1].");
    }

    @Test
    @DisplayName("AI 명세의 편성 결과 예시는 목표 15분 · 근거 문장 · 카탈로그에 없는 클립 영상 그대로 제안이 된다")
    void AI_명세의_편성_결과_예시는_목표_15분_근거_문장_클립_영상_그대로_제안이_된다() {
        CoachRunResult.Proposal proposal = new CoachRunResult.Proposal(
                List.of(specExample()),
                List.of(new Citation(1, "국민체력100 운동처방 · 유소년 11세", "prescription:유소년-11-F-0142", null)),
                List.of());

        CoachProposalItem item = converter.convert(proposal).getFirst();

        assertThat(item.position()).isEqualTo(0);
        assertThat(item.title()).isEqualTo("월요일 늘이기");
        assertThat(item.targetMetric()).isEqualTo("TIMER_MINUTES");
        assertThat(item.targetValue()).isEqualTo(15);
        assertThat(item.rationale()).isEqualTo("또래 처방에 나온 늘이는 동작을 앞세워 골랐습니다 [1].");
        assertThat(item.description()).startsWith("준비운동 넙다리 안쪽 늘리기 (나비자세) · 준비운동 척추 들어올리기 (고양이자세)");
        assertThat(item.startsOn()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(item.endsOn()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(item.video()).isEqualTo(new ProposalVideo("Eg3GpTv7z8s", 144));
        assertThat(item.participants())
                .containsExactly(new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자"));
        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1);
        assertThat(item.copyChild()).isEqualTo("이번 주는 몸을 길게 늘이는 동작, 엄마랑 같이 해볼까요");
        assertThat(item.copyParent()).isEqualTo("유연성은 지금 키우기 좋은 영역입니다. 주 3회 15분이면 충분합니다");
    }

    @Test
    @DisplayName(
            "세션은 칸이 된다 — 차례대로 position 1..n, 한글 단계는 WARMUP · MAIN · COOLDOWN, 분은 준비 · 정리 1분에 남는 분을 본운동이 나눠 합이 목표 분이다")
    void 세션은_칸이_되고_분은_준비_정리_1분_남는_분을_본운동이_나눈다() {
        ProposalConverter titled = new ProposalConverter(
                family.child.profileId(),
                ProfileRole.CHILD,
                null,
                ClipTitles.of(Map.of("Eg3GpTv7z8s-144", "넙다리 안쪽 늘리기 (나비자세)"), Map.of()));

        CoachProposalItem item =
                titled.convert(proposal(List.of(specExample()))).getFirst();

        assertThat(item.sessions().stream().map(MissionSession::position).toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(item.sessions().stream().map(MissionSession::phase).toList())
                .containsExactly(WARMUP, WARMUP, MAIN, MAIN, MAIN, MAIN, COOLDOWN);
        assertThat(item.sessions().stream().map(MissionSession::minutes).toList())
                .containsExactly(1, 1, 3, 3, 3, 3, 1);
        assertThat(item.targetValue()).isEqualTo(MissionSession.totalMinutes(item.sessions()));
        assertThat(item.sessions().get(4).factor()).isEqualTo(FitnessFactor.STRENGTH);
        assertThat(item.sessions().getFirst())
                .isEqualTo(new MissionSession(
                        1,
                        WARMUP,
                        "넙다리 안쪽 늘리기 (나비자세)",
                        FitnessFactor.FLEXIBILITY,
                        1,
                        new SessionClip("Eg3GpTv7z8s", 144, 182, "넙다리 안쪽 늘리기 (나비자세)")));
        // 제목 조회에 없는 구간은 제목 없이 사본만
        assertThat(item.sessions().getLast().clip()).isEqualTo(new SessionClip("Eg3GpTv7z8s", 1426, 1466, null));
    }

    @Test
    @DisplayName("칸 차례는 (day_offset, order) 다 — AI order 는 날마다 1부터 다시 세므로 position 을 새로 매긴다. order 가 없으면 그날 맨 뒤")
    void 칸_차례는_day_offset_다음_order_이고_position_을_새로_매긴다() {
        CoachRunResult.Mission mission = mission(
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자")),
                20,
                List.of(
                        new CoachRunResult.Session(1, "본운동", 1, "둘째 날 첫째", "", 40, null, List.of()),
                        new CoachRunResult.Session(0, "본운동", null, "첫째 날 차례 없음", "", 40, null, List.of()),
                        new CoachRunResult.Session(0, "본운동", 2, "첫째 날 둘째", "", 40, null, List.of()),
                        new CoachRunResult.Session(null, "본운동", 1, "첫째 날 첫째", "", 40, null, List.of())),
                "부모 문구",
                "");

        CoachProposalItem item = converter.convert(proposal(List.of(mission))).getFirst();

        assertThat(item.sessions().stream().map(MissionSession::title).toList())
                .containsExactly("첫째 날 첫째", "첫째 날 둘째", "첫째 날 차례 없음", "둘째 날 첫째");
        assertThat(item.sessions().stream().map(MissionSession::position).toList())
                .containsExactly(1, 2, 3, 4);
        assertThat(item.sessions().stream().map(MissionSession::minutes).toList())
                .containsExactly(5, 5, 5, 5);
    }

    @Test
    @DisplayName("요인이 비었거나 모르는 이름이면 null, 이름이 비면 구간 제목 → 단계 이름, 구간 끝이 시작보다 앞이면 끝을 버리고, 영상 id 가 칸 표에 안 맞으면 영상을 붙이지 않는다")
    void 칸의_빈_값과_맞지_않는_값을_지어내지_않고_거른다() {
        ProposalConverter titled = new ProposalConverter(
                family.child.profileId(),
                ProfileRole.CHILD,
                null,
                ClipTitles.of(Map.of(), Map.of("IdpXx2gm90o", "초등학생 기초체력")));
        CoachRunResult.Mission mission = mission(
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자")),
                10,
                List.of(
                        new CoachRunResult.Session(
                                0,
                                "준비운동",
                                1,
                                " ",
                                "",
                                40,
                                new CoachRunResult.Video("IdpXx2gm90o", null, 30),
                                List.of()),
                        new CoachRunResult.Session(
                                0, "본운동", 2, "", "모르는 힘", 40, new CoachRunResult.Video("a/b", 0, 30), List.of()),
                        new CoachRunResult.Session(
                                0, "", 3, "스쿼트", "근력", 40, new CoachRunResult.Video("IdpXx2gm90o", 90, 90), List.of())),
                "부모 문구",
                "");

        List<MissionSession> sessions =
                titled.convert(proposal(List.of(mission))).getFirst().sessions();

        assertThat(sessions.getFirst())
                .isEqualTo(new MissionSession(
                        1, WARMUP, "초등학생 기초체력", null, 1, new SessionClip("IdpXx2gm90o", 0, 30, "초등학생 기초체력")));
        assertThat(sessions.get(1)).isEqualTo(new MissionSession(2, MAIN, "본운동", null, 5, null));
        assertThat(sessions.getLast())
                .isEqualTo(new MissionSession(
                        3,
                        MAIN,
                        "스쿼트",
                        FitnessFactor.STRENGTH,
                        4,
                        new SessionClip("IdpXx2gm90o", 90, null, "초등학생 기초체력")));
    }

    @Test
    @DisplayName("본운동 칸이 없으면 목 규칙대로 준비 · 정리 1분씩이고 목표 분은 그 합이다 — 요청 분과 달라진다")
    void 본운동_칸이_없으면_준비_정리_1분씩이고_목표_분은_그_합이다() {
        CoachRunResult.Mission mission = mission(
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자")),
                15,
                List.of(
                        clipSession("준비운동", 1, "넙다리 안쪽 늘리기 (나비자세)", "유연성", "Eg3GpTv7z8s", 144, 182),
                        clipSession("정리운동", 2, "다리 뒤 늘리기", "유연성", "Eg3GpTv7z8s", 1426, 1466)),
                "부모 문구",
                "");

        CoachProposalItem item = converter.convert(proposal(List.of(mission))).getFirst();

        assertThat(item.sessions().stream().map(MissionSession::minutes).toList())
                .containsExactly(1, 1);
        assertThat(item.targetValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("구간 제목은 클립 표(clipId = videoId-startSec)가 먼저, 없으면 영상 표의 영상 제목, 둘 다 없으면 null")
    void 구간_제목은_클립_표가_먼저_없으면_영상_제목() {
        ClipTitles titles = ClipTitles.of(Map.of("v1-10", "스쿼트"), Map.of("v1", "영상 한 편", "v2", "다른 영상"));

        assertThat(titles.titleOf("v1", 10)).isEqualTo("스쿼트");
        assertThat(titles.titleOf("v1", 11)).isEqualTo("영상 한 편");
        assertThat(titles.titleOf("v2", 10)).isEqualTo("다른 영상");
        assertThat(titles.titleOf("v3", 10)).isNull();
        assertThat(ClipTitles.none().titleOf("v1", 10)).isNull();
    }

    @Test
    @DisplayName("세션은 order 순으로 보고 영상 없는 칸을 건너뛰어 첫 영상을 고르며, 참여자는 대상만 남기고 인용은 evidence 로 거른다")
    void 세션은_order_순으로_보고_첫_영상을_고르며_참여자는_대상만_남기고_인용은_evidence_로_거른다() {
        CoachRunResult.Mission mission = mission(
                List.of(
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자"),
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.parent.profileId()), "동반자"),
                        new CoachRunResult.ParticipantRef("p_unknown", "주행자")),
                20,
                List.of(
                        session(3, new CoachRunResult.Video("later", 0, 30), List.of()),
                        session(1, null, List.of(1)),
                        session(2, new CoachRunResult.Video("IdpXx2gm90o", 96, 136), List.of(1, 2))),
                "부모 문구",
                "골랐습니다 [1].");

        CoachProposalItem item = converter.convert(proposal(List.of(mission))).getFirst();

        assertThat(item.video()).isEqualTo(new ProposalVideo("IdpXx2gm90o", 96));
        assertThat(item.targetValue()).isEqualTo(20);
        assertThat(item.participants())
                .containsExactly(new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자"));
        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("AI 가 넣은 응원 부모는 빼고, withParent 면 요청한 보호자를 동반자로 덧붙인다")
    void 응원_부모는_빼고_withParent_면_요청한_보호자를_동반자로_덧붙인다() {
        ProposalConverter withParent =
                new ProposalConverter(family.child.profileId(), ProfileRole.CHILD, family.parent.profileId());
        CoachRunResult.Mission mission = mission(
                List.of(
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "주행자"),
                        new CoachRunResult.ParticipantRef(ProfileRef.of(family.cheerParent.profileId()), "응원")),
                20,
                List.of(session(1, null, List.of(1))),
                "부모 문구",
                "골랐습니다 [1].");

        CoachProposalItem item = withParent.convert(proposal(List.of(mission))).getFirst();

        assertThat(item.participants())
                .containsExactly(
                        new ProposalParticipant(family.child.profileId(), ProfileRole.CHILD, "주행자"),
                        new ProposalParticipant(family.parent.profileId(), ProfileRole.PARENT, "동반자"));
    }

    @Test
    @DisplayName("대상이 들지 않은 미션은 참여자가 비어 승인 때 미션이 되지 않는다 — 보호자만 남기지 않는다")
    void 대상이_들지_않은_미션은_참여자가_비어_있다() {
        ProposalConverter withParent =
                new ProposalConverter(family.child.profileId(), ProfileRole.CHILD, family.parent.profileId());
        CoachRunResult.Mission mission = mission(
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.parent.profileId()), "동반자")),
                20,
                List.of(),
                "부모 문구",
                "");

        assertThat(withParent.convert(proposal(List.of(mission))).getFirst().participants())
                .isEmpty();
    }

    @Test
    @DisplayName("duration_min 이 없으면 목표는 최소 1분, reason 이 비면 부모 문구, evidence 가 없으면 실행 전체 인용")
    void duration_min_이_없으면_목표는_최소_1분_reason_이_비면_부모_문구_evidence_가_없으면_전체_인용() {
        CoachRunResult.Mission mission = mission(
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(family.child.profileId()), "")),
                null,
                List.of(session(null, null, List.of())),
                "부모 문구",
                "");

        CoachProposalItem item = converter.convert(proposal(List.of(mission))).getFirst();

        assertThat(item.targetValue()).isEqualTo(1);
        assertThat(item.rationale()).isEqualTo("부모 문구");
        assertThat(item.video()).isNull();
        assertThat(item.citations().stream().map(ProposalCitation::index).toList())
                .containsExactly(1, 2, 3);
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
                toddler,
                Map.of("012", new BigDecimal("5.5"), "005", new BigDecimal("80")),
                null,
                null,
                null,
                null,
                Fixed.TODAY);

        assertThat(profile.profileRef()).isEqualTo(ProfileRef.of(toddler.profileId()));
        assertThat(profile.age()).isEqualTo(38);
        assertThat(profile.ageUnit()).isEqualTo("개월");
        assertThat(profile.sex()).isEqualTo("M");
        assertThat(profile.measurements()).containsOnlyKeys("012");
        assertThat(profile.inputLevel()).isEqualTo("L2");

        AiProfile child = AiProfileFactory.of(
                family.child, Map.of(), new BigDecimal("140.5"), new BigDecimal("35"), null, null, Fixed.TODAY);
        assertThat(child.age()).isEqualTo(11);
        assertThat(child.ageUnit()).isEqualTo("세");
        assertThat(child.inputLevel()).isEqualTo("L1");
        // 체지방률 003 · 허리둘레 004 는 적은 것만 measurements 에 싣는다 — AI 가 3등급(BMI · 체지방률 · WHtR) 판정에 쓴다
        AiProfile withBody = AiProfileFactory.of(
                family.child,
                Map.of("012", new BigDecimal("8")),
                new BigDecimal("140.5"),
                new BigDecimal("35"),
                new BigDecimal("22.5"),
                new BigDecimal("61.2"),
                Fixed.TODAY);
        assertThat(withBody.measurements())
                .containsOnly(Map.entry("012", 8.0), Map.entry("003", 22.5), Map.entry("004", 61.2));
        AiProfile waistOnly =
                AiProfileFactory.of(family.child, Map.of(), null, null, null, new BigDecimal("61.2"), Fixed.TODAY);
        assertThat(waistOnly.measurements()).containsOnly(Map.entry("004", 61.2));
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
                proposal(List.of(mission(List.of(), 15, List.of(), "부모 요약", ""))),
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
