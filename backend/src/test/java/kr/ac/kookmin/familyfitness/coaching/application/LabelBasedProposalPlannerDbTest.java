package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대체 편성이 H2 + Flyway 의 실제 클립 표(V132 유튜브 · V161 공단)에서 고른다. 측정은 없고(목이 null) 보호자가 키워 주고 싶은 역량만 준다.
 * 공단 영상 클립을 고르면 칸 video 에 source=kspo 와 mp4 주소가, 인용에 AI 와 같은 "kspo:&lt;id&gt;" 가 실려야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LabelBasedProposalPlannerDbTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Autowired
    LabelBasedProposalPlanner planner;

    @MockitoBean
    FitnessQuery fitnessQuery;

    private static ProfileDetails subject(LocalDate birthDate) {
        return new ProfileDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "대상",
                ProfileRole.PARENT,
                birthDate,
                Sex.F,
                null,
                null,
                null,
                true);
    }

    private static List<CoachRunResult.Session> sessionsOf(CoachRunResult result) {
        CoachRunResult.Proposal proposal = result.proposal();
        assertThat(proposal).isNotNull();
        return proposal.missions().getFirst().sessions();
    }

    @Test
    @DisplayName("어르신은 어르신 공단 영상 클립을 먼저 받고, 칸 영상은 source=kspo · mp4 주소, 인용은 kspo:<id> 다")
    void 어르신은_어르신_공단_영상_클립을_먼저_받는다() {
        CoachRunResult result = planner.plan(
                subject(LocalDate.of(1950, 3, 1)),
                TODAY,
                new CoachRunConditions(20, false, null, FitnessFactor.BALANCE, false),
                TODAY,
                "시험");

        assertThat(result).isNotNull();
        List<CoachRunResult.Session> sessions = sessionsOf(result);
        assertThat(sessions).isNotEmpty().allSatisfy(it -> {
            CoachRunResult.Video video = it.video();
            assertThat(video).isNotNull();
            assertThat(video.source()).isEqualTo("kspo");
            assertThat(video.mediaUrl()).isEqualTo("https://openapi.kspo.or.kr/web/video/" + video.videoId() + ".mp4");
            assertThat(video.startSec()).isZero();
        });
        assertThat(sessions).anySatisfy(it -> assertThat(it.fitnessFactor()).isEqualTo("평형성"));
        List<Citation> citations = result.proposal().citations();
        assertThat(citations).isNotEmpty().allSatisfy(it -> {
            assertThat(it.chunkId()).startsWith("kspo:");
            assertThat(it.label()).startsWith("국민체력100 동영상 정보 · ");
            assertThat(it.url()).startsWith("https://openapi.kspo.or.kr/web/video/");
        });
    }

    @Test
    @DisplayName("어르신 근력 편성의 본운동은 근력 클립이다 — 연령대보다 요인이 먼저다(ai:video/catalog.py _age_rank)")
    void 어르신_근력_편성은_본운동에_근력_클립을_고른다() {
        CoachRunResult result = planner.plan(
                subject(LocalDate.of(1956, 3, 1)),
                TODAY,
                new CoachRunConditions(20, false, null, FitnessFactor.STRENGTH, false),
                TODAY,
                "시험");

        assertThat(result).isNotNull();
        assertThat(sessionsOf(result))
                .filteredOn(it -> it.phase().equals("본운동"))
                .isNotEmpty()
                .allSatisfy(it -> assertThat(it.fitnessFactor()).isEqualTo("근력"));
    }

    @Test
    @DisplayName("유소년 순발력 편성은 본운동에 공단 영상 클립을 고를 수 있고, 준비 · 정리는 유튜브 구간으로 채운다")
    void 유소년_순발력_편성은_공단_영상과_유튜브_구간을_함께_쓴다() {
        CoachRunResult result = planner.plan(
                subject(LocalDate.of(2016, 5, 1)),
                TODAY,
                new CoachRunConditions(20, false, null, FitnessFactor.POWER, false),
                TODAY,
                "시험");

        assertThat(result).isNotNull();
        List<CoachRunResult.Session> sessions = sessionsOf(result);
        assertThat(sessions)
                .filteredOn(it -> it.phase().equals("본운동"))
                .anySatisfy(it -> assertThat(it.video().isKspo()).isTrue());
        assertThat(sessions).anySatisfy(it -> {
            assertThat(it.video().isKspo()).isFalse();
            assertThat(it.video().mediaUrl()).isNull();
        });
        assertThat(result.proposal().citations())
                .extracting(Citation::chunkId)
                .anySatisfy(it -> assertThat(it).startsWith("kspo:"))
                .anySatisfy(it -> assertThat(it).startsWith("video:"));
    }
}
