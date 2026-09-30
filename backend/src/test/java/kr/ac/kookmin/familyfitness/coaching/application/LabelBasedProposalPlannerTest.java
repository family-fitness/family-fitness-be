package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.support.FakeFitness;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 클립 표가 비어 영상 한 편을 통째로 짜는 대체 편성(wholeVideoPlan). 측정은 없고 보호자가 키워 주고 싶은 역량만 준다. */
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
}
