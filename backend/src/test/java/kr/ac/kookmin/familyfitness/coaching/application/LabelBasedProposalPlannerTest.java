package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel;
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 클립 표가 비어 영상 한 편을 통째로 짜는 대체 편성(wholeVideoPlan). 측정은 없고 보호자가 키워 주고 싶은 역량만 준다.
 * 아래쪽은 클립으로 짜는 편성에서 보호자가 키워 주고 싶은 역량이 본운동 칸의 4분의 3 이상을 채우는지 본다.
 */
class LabelBasedProposalPlannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final CoachRunConditions STRENGTH =
            new CoachRunConditions(20, false, null, FitnessFactor.STRENGTH, false);

    private final InMemoryExerciseVideoRepository videos = new InMemoryExerciseVideoRepository(
            List.of(Videos.video("adult", 19, 64, "근력", 300), Videos.video("youth", 7, 12, "근력", 100)));
    private final FakeFitness fitness = new FakeFitness();
    private final LabelBasedProposalPlanner planner =
            new LabelBasedProposalPlanner(fitness, videos, new InMemoryExerciseClipRepository());

    private static ProfileDetails grandpa() {
        return new ProfileDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "할아버지",
                ProfileRole.PARENT,
                LocalDate.of(1950, 3, 1),
                Sex.M,
                null,
                null,
                null,
                true);
    }

    private static CoachRunResult.Video mainVideo(CoachRunResult result) {
        assertThat(result).isNotNull();
        CoachRunResult.Proposal proposal = result.proposal();
        assertThat(proposal).isNotNull();
        CoachRunResult.Session session =
                proposal.missions().getFirst().sessions().getFirst();
        assertThat(session.phase()).isEqualTo("본운동");
        CoachRunResult.Video video = session.video();
        assertThat(video).isNotNull();
        return video;
    }

    @Test
    @DisplayName("어르신은 성인 범위(19~64) 영상 한 편을 받는다 — 공단 어르신 영상을 싣지 않은 뒤로 영상도 인용도 없어 편성이 실패했다")
    void 어르신은_성인_범위_영상_한_편을_받는다() {
        CoachRunResult result = planner.plan(grandpa(), TODAY, STRENGTH, TODAY, "시험", List.of());

        assertThat(mainVideo(result).videoId()).isEqualTo("adult");
    }

    @Test
    @DisplayName("측정으로 고른 요인은 「지금 키우기 좋은 영역」 이라 부르고 백분위 구간 문구를 쓰지 않는다 — 가장 낮은 요인인데 「꾸준히 하고 있는 영역」 이라 불렀다")
    void 측정으로_고른_요인은_지금_키우기_좋은_영역이다() {
        ProfileDetails child = new ProfileDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "하윤",
                ProfileRole.CHILD,
                LocalDate.of(2015, 5, 1),
                Sex.F,
                null,
                null,
                null,
                true);
        fitness.measured(child.profileId(), new FactorPoint(FitnessFactor.STRENGTH, "012", 50), null);

        CoachRunResult result = planner.plan(
                child, TODAY, new CoachRunConditions(20, false, null, null, false), TODAY, "시험", List.of());

        assertThat(result).isNotNull();
        String parentCopy = result.proposal().missions().getFirst().copyParent();
        assertThat(parentCopy)
                .isEqualTo("지금 키우기 좋은 영역인 근력을 기르는 동작으로 20분을 짰습니다")
                .doesNotContain(Band.ofPercentile(50).getCopy());
        assertThat(result.steps().getFirst().summary()).endsWith("대상 요인 = 근력(지금 키우기 좋은 영역)");
    }

    @Test
    @DisplayName("보호자가 고른 요인은 「보호자가 키워 주고 싶은 역량」 이라 부른다 — 「고르신 …」 은 누가 골랐는지 흐렸다")
    void 보호자가_고른_요인은_보호자가_키워_주고_싶은_역량이다() {
        CoachRunResult result = planner.plan(grandpa(), TODAY, STRENGTH, TODAY, "시험", List.of());

        assertThat(result).isNotNull();
        assertThat(result.proposal().missions().getFirst().copyParent())
                .isEqualTo("보호자가 키워 주고 싶은 역량인 근력을 기르는 동작으로 20분을 짰습니다");
        assertThat(result.steps().getFirst().summary()).endsWith("대상 요인 = 근력(보호자가 키워 주고 싶은 역량)");
    }

    @Test
    @DisplayName("통째 영상 편성도 최근 받은 영상은 뒤로 미룬다 — 다른 후보가 있으면 그것을 고른다")
    void 통째_영상도_최근_받은_영상은_뒤로_미룬다() {
        videos.videos.put("adult2", Videos.video("adult2", 19, 64, "근력", 400));

        CoachRunResult result = planner.plan(grandpa(), TODAY, STRENGTH, TODAY, "시험", List.of("adult"));

        assertThat(mainVideo(result).videoId()).isEqualTo("adult2");
    }

    @Test
    @DisplayName("어르신을 겨냥한 영상이 있으면 연령 범위가 더 넓어도 성인 영상보다 먼저 받는다")
    void 어르신을_겨냥한_영상을_먼저_받는다() {
        videos.videos.put("senior", Videos.video("senior", 65, 120, "근력", 600));

        CoachRunResult result = planner.plan(grandpa(), TODAY, STRENGTH, TODAY, "시험", List.of());

        assertThat(mainVideo(result).videoId()).isEqualTo("senior");
    }

    // ---- 보호자가 키워 주고 싶은 역량의 본운동 몫(4분의 3 이상) ----

    private static final UUID CHILD_ID = UUID.randomUUID();

    private static ProfileDetails child() {
        return new ProfileDetails(
                CHILD_ID,
                UUID.randomUUID(),
                null,
                "하윤",
                ProfileRole.CHILD,
                LocalDate.of(2016, 5, 1),
                Sex.F,
                null,
                null,
                null,
                true);
    }

    /** 유소년 클립 한 편(0~60초). 처방 어휘 이름(exerciseName)을 주면 순위 점수가 1 앞선다. */
    private static ExerciseClip clip(
            String videoId, String title, SessionPhase phase, FitnessFactor factor, String exerciseName) {
        ExerciseClip base = InMemoryExerciseClipRepository.clip(videoId, 0, 60, title, phase, factor, AgeGroup.YOUTH);
        return new ExerciseClip(
                base.clipId(),
                base.videoId(),
                base.seq(),
                base.nameOnVideo(),
                exerciseName,
                base.title(),
                base.factor(),
                base.phase(),
                base.startSec(),
                base.endSec(),
                base.homeOk(),
                base.quiet(),
                base.needsProps(),
                base.isExercise(),
                base.ageGroup(),
                base.source(),
                base.active(),
                base.media());
    }

    /**
     * 준비 2 · 정리 1 과 본운동 후보(근력 focusCount 개는 처방 어휘 이름 없음, 심폐지구력 4개는 있음)로 짜는 편성기.
     * 측정에서 가장 낮은 요인은 심폐지구력이다.
     */
    private LabelBasedProposalPlanner clipPlanner(int focusCount) {
        InMemoryExerciseClipRepository clips = new InMemoryExerciseClipRepository(
                clip("w1", "팔 돌리기", SessionPhase.WARMUP, FitnessFactor.FLEXIBILITY, null),
                clip("w2", "목 돌리기", SessionPhase.WARMUP, FitnessFactor.FLEXIBILITY, null),
                clip("c1", "숨 고르기", SessionPhase.COOLDOWN, FitnessFactor.FLEXIBILITY, null));
        for (int i = 1; i <= focusCount; i++) {
            ExerciseClip it = clip("s" + i, "근력 동작 " + i, SessionPhase.MAIN, FitnessFactor.STRENGTH, null);
            clips.clips.put(it.clipId(), it);
        }
        for (int i = 1; i <= 4; i++) {
            ExerciseClip it = clip("k" + i, "심폐 동작 " + i, SessionPhase.MAIN, FitnessFactor.CARDIO, "달리기");
            clips.clips.put(it.clipId(), it);
        }
        fitness.measured(CHILD_ID, new FactorPoint(FitnessFactor.CARDIO, "001", 10), null);
        return new LabelBasedProposalPlanner(fitness, videos, clips);
    }

    private static List<CoachRunResult.Session> mainSessions(CoachRunResult result) {
        assertThat(result).isNotNull();
        return result.proposal().missions().getFirst().sessions().stream()
                .filter(it -> it.phase().equals("본운동"))
                .toList();
    }

    private static long factorCount(List<CoachRunResult.Session> sessions, FitnessFactor factor) {
        return sessions.stream()
                .filter(it -> it.fitnessFactor().equals(factor.getLabel()))
                .count();
    }

    @Test
    @DisplayName("보호자가 키워 주고 싶은 역량을 골랐으면 본운동 네 칸 중 세 칸 이상이 그 역량이다 — 다른 요인 클립에 처방 어휘 이름이 있어도 밀리지 않는다")
    void 보호자가_고른_역량이_본운동_4분의_3_이상이다() {
        CoachRunResult result = clipPlanner(4).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of());

        List<CoachRunResult.Session> main = mainSessions(result);
        assertThat(main).hasSize(4);
        assertThat(factorCount(main, FitnessFactor.STRENGTH)).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("그 역량 클립을 모두 최근에 받았어도 그 역량으로 먼저 채운다 — 최근 영상 뒤로 미루기는 같은 역량 안에서만 차례를 바꾼다")
    void 최근에_받은_역량_클립이라도_먼저_채운다() {
        CoachRunResult result = clipPlanner(3).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of("s1", "s2", "s3"));

        List<CoachRunResult.Session> main = mainSessions(result);
        assertThat(main).hasSize(4);
        assertThat(factorCount(main, FitnessFactor.STRENGTH)).isEqualTo(3);
        // 첫 본운동 칸(미션 대표 영상)도 그 역량이다
        assertThat(main.getFirst().fitnessFactor()).isEqualTo("근력");
    }

    @Test
    @DisplayName("본운동 칸 수가 넷이 아니면 4분의 3 을 올림한다 — 60분 편성은 본운동 여섯 칸 중 다섯 칸 이상")
    void 본운동_칸_수가_다르면_올림한다() {
        CoachRunResult result = clipPlanner(6)
                .plan(
                        child(),
                        TODAY,
                        new CoachRunConditions(60, false, null, FitnessFactor.STRENGTH, false),
                        TODAY,
                        "시험",
                        List.of("s1", "s2", "s3", "s4", "s5", "s6"));

        List<CoachRunResult.Session> main = mainSessions(result);
        assertThat(main).hasSize(6);
        assertThat(factorCount(main, FitnessFactor.STRENGTH)).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("그 역량 몫은 본운동 칸 수의 4분의 3 올림이다 — 세 칸이면 셋, 네 칸이면 셋, 다섯 칸이면 넷, 여섯 칸이면 다섯")
    void 역량_몫은_4분의_3_올림이다() {
        assertThat(List.of(3, 4, 5, 6).stream().map(LabelBasedProposalPlanner::focusShare))
                .containsExactly(3, 3, 4, 5);
    }

    @Test
    @DisplayName("그 역량 클립이 모자라면 있는 만큼 넣고 나머지 칸은 다른 요인으로 채운다")
    void 역량_클립이_모자라면_다른_요인으로_채운다() {
        CoachRunResult result = clipPlanner(1).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of("s1"));

        List<CoachRunResult.Session> main = mainSessions(result);
        assertThat(main).hasSize(4);
        assertThat(factorCount(main, FitnessFactor.STRENGTH)).isEqualTo(1);
        assertThat(factorCount(main, FitnessFactor.CARDIO)).isEqualTo(3);
    }

    @Test
    @DisplayName("그 역량 클립이 모두 최근 영상이면 오래전에 받은 것부터 쓴다 — 순위대로 두면 날마다 같은 상위 클립이 앞에 섰다")
    void 최근_영상끼리는_오래전에_받은_것부터_쓴다() {
        // 최근 것부터: s1 이 어제, s6 이 가장 오래전에 받은 영상
        CoachRunResult result =
                clipPlanner(6).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of("s1", "s2", "s3", "s4", "s5", "s6"));

        assertThat(mainSessions(result).stream().map(it -> it.video().videoId()))
                .containsExactly("s6", "s5", "s4", "s3");
    }

    @Test
    @DisplayName("최근에 받지 않은 영상이 먼저이고, 최근 영상끼리는 오래전에 받은 것부터다")
    void 새_영상_다음에_오래된_최근_영상이다() {
        CoachRunResult result =
                clipPlanner(6).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of("s1", "s2", "s3", "s4"));

        List<String> main =
                mainSessions(result).stream().map(it -> it.video().videoId()).toList();
        assertThat(main.subList(0, 2)).containsExactlyInAnyOrder("s5", "s6");
        assertThat(main.subList(2, 4)).containsExactly("s4", "s3");
    }

    /** 본운동 근력 후보: 한 영상(same)에서 나온 클립 둘이 세트 길이(60초)에 맞아 순위가 가장 앞이고, 다른 영상 셋은 90초라 뒤다. */
    private static InMemoryExerciseClipRepository sameVideoClips(boolean withOthers) {
        InMemoryExerciseClipRepository clips = new InMemoryExerciseClipRepository(
                clip("w1", "팔 돌리기", SessionPhase.WARMUP, FitnessFactor.FLEXIBILITY, null),
                clip("w2", "목 돌리기", SessionPhase.WARMUP, FitnessFactor.FLEXIBILITY, null),
                clip("c1", "숨 고르기", SessionPhase.COOLDOWN, FitnessFactor.FLEXIBILITY, null),
                InMemoryExerciseClipRepository.clip(
                        "same", 0, 60, "스쿼트", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH),
                InMemoryExerciseClipRepository.clip(
                        "same", 60, 120, "런지", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH),
                InMemoryExerciseClipRepository.clip(
                        "same", 120, 180, "벽 밀기", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH),
                InMemoryExerciseClipRepository.clip(
                        "same", 180, 240, "팔굽혀펴기", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH));
        if (withOthers) {
            for (int i = 1; i <= 3; i++) {
                ExerciseClip it = InMemoryExerciseClipRepository.clip(
                        "o" + i, 0, 90, "다른 근력 " + i, SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH);
                clips.clips.put(it.clipId(), it);
            }
        }
        return clips;
    }

    private LabelBasedProposalPlanner planner(InMemoryExerciseClipRepository clips) {
        return new LabelBasedProposalPlanner(fitness, videos, clips);
    }

    @Test
    @DisplayName("한 회 안에서 같은 영상을 두 번 쓰지 않는다 — 같은 영상의 다른 클립이 순위가 앞서도 다른 영상이 있으면 그것을 쓴다")
    void 한_회_안에서_같은_영상을_두_번_쓰지_않는다() {
        CoachRunResult result = planner(sameVideoClips(true)).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of());

        List<String> main =
                mainSessions(result).stream().map(it -> it.video().videoId()).toList();
        assertThat(main.getFirst()).isEqualTo("same");
        assertThat(main.subList(1, 4)).containsExactlyInAnyOrder("o1", "o2", "o3");
    }

    @Test
    @DisplayName("다른 영상이 없으면 한 회 안에서 같은 영상의 다른 클립을 다시 쓴다 — 칸을 비우지 않는다")
    void 다른_영상이_없으면_같은_영상을_다시_쓴다() {
        CoachRunResult result = planner(sameVideoClips(false)).plan(child(), TODAY, STRENGTH, TODAY, "시험", List.of());

        List<CoachRunResult.Session> main = mainSessions(result);
        assertThat(main).hasSize(4);
        assertThat(main.stream().map(it -> it.video().videoId())).containsOnly("same");
    }

    @Test
    @DisplayName("같은 영상을 피해도 키울 요인이 먼저다 — 다른 요인 클립이 새 영상이어도 같은 영상의 그 요인 클립보다 뒤다")
    void 같은_영상을_피해도_요인이_먼저다() {
        InMemoryExerciseClipRepository clips = sameVideoClips(false);
        for (int i = 1; i <= 3; i++) {
            ExerciseClip it = InMemoryExerciseClipRepository.clip(
                    "k" + i, 0, 60, "심폐 동작 " + i, SessionPhase.MAIN, FitnessFactor.CARDIO, AgeGroup.YOUTH);
            clips.clips.put(it.clipId(), it);
        }
        // 측정에서 가장 낮은 요인은 근력 — 근력 후보는 모두 same 한 영상에서 나왔다
        fitness.measured(CHILD_ID, new FactorPoint(FitnessFactor.STRENGTH, "012", 10), null);

        CoachRunResult result = planner(clips)
                .plan(child(), TODAY, new CoachRunConditions(20, false, null, null, false), TODAY, "시험", List.of());

        assertThat(factorCount(mainSessions(result), FitnessFactor.STRENGTH)).isEqualTo(4);
    }

    @Test
    @DisplayName("보호자가 고르지 않았으면 지금처럼 측정에서 가장 낮은 요인으로 본운동을 채운다")
    void 보호자가_고르지_않으면_가장_낮은_요인이다() {
        CoachRunResult result = clipPlanner(4)
                .plan(child(), TODAY, new CoachRunConditions(20, false, null, null, false), TODAY, "시험", List.of());

        List<CoachRunResult.Session> main = mainSessions(result);
        assertThat(main).hasSize(4);
        assertThat(factorCount(main, FitnessFactor.CARDIO)).isEqualTo(4);
        assertThat(result.proposal().missions().getFirst().title()).startsWith("심폐지구력 키우기");
    }

    @Test
    @DisplayName("유튜브 영상 근거 이름은 제목의 이모지와 앞쪽 대괄호 머리말을 걷고 영어 길이 표시를 분으로 바꾼다")
    void 유튜브_영상_근거_이름은_제목을_다듬어_쓴다() {
        videos.videos.clear();
        videos.videos.put(
                "raw",
                new ExerciseVideo(
                        "raw",
                        "[👦🏻유소년] 성장기 학생들을 위한 체력향상 운동프로그램 (30min)",
                        "국민체력100",
                        "PUBLIC",
                        300,
                        new VideoLabel(19, 64, VideoLabel.parseFactors("근력"), "LOW", "SMALL_ROOM", "QUIET", null),
                        null,
                        "SEED",
                        Instant.parse("2026-09-01T00:00:00Z")));

        CoachRunResult result = planner.plan(grandpa(), TODAY, STRENGTH, TODAY, "시험", List.of());

        assertThat(result).isNotNull();
        assertThat(result.proposal().citations())
                .extracting(Citation::label)
                .contains("국민체력100 운동영상, 성장기 학생들을 위한 체력향상 운동프로그램(30분)");
    }

    @Test
    @DisplayName("근거 이름 다듬기는 제목 속 세로 막대를 쉼표로 바꾸고 겹친 띄어쓰기를 하나로 줄인다")
    void 근거_이름_다듬기는_세로_막대와_겹친_띄어쓰기를_고친다() {
        assertThat(LabelBasedProposalPlanner.youtubeCitationTitle("🦖유아기 복합 지각능력  향상 활동 | EP01. 알맞게 놓아요! (25min)"))
                .isEqualTo("유아기 복합 지각능력 향상 활동, EP01. 알맞게 놓아요!(25분)");
        assertThat(LabelBasedProposalPlanner.youtubeCitationTitle("[🌱키쑥쑥 성장기] 스트레칭 (20 MIN)"))
                .isEqualTo("스트레칭(20분)");
        assertThat(LabelBasedProposalPlanner.youtubeCitationTitle("[머리말만]")).isEqualTo("[머리말만]");
    }
}
