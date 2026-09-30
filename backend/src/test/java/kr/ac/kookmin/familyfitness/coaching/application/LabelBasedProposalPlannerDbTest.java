package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
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
 * 대체 편성이 H2 + Flyway 의 실제 클립 표(V132 유튜브 · V161 ~ V165 공단)에서 고른다. 측정은 없고(목이 null) 보호자가 키워 주고 싶은 역량만 준다.
 * 공단 영상 클립을 고르면 칸 video 에 source=kspo 와 mp4 주소가, 인용에 AI 와 같은 "kspo:&lt;id&gt;" 와 AI 표의 인용 이름이 실려야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LabelBasedProposalPlannerDbTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Autowired
    LabelBasedProposalPlanner planner;

    @Autowired
    ExerciseClipRepository clips;

    @MockitoBean
    FitnessQuery fitnessQuery;

    /** 칸 영상이 가리키는 클립(clipId = videoId-startSec). */
    private ExerciseClip clipOf(CoachRunResult.Session session) {
        CoachRunResult.Video video = session.video();
        assertThat(video).isNotNull();
        ExerciseClip clip = clips.findById(ExerciseClip.idOf(video.videoId(), video.startSec()));
        assertThat(clip).as("칸 클립 %s", video.videoId()).isNotNull();
        return clip;
    }

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
    @DisplayName("어르신은 성인(공통) 공단 영상 클립을 받고, 칸 영상은 source=kspo · mp4 주소, 인용은 kspo:<id> 다")
    void 어르신은_성인_공단_영상_클립을_받는다() {
        CoachRunResult result = planner.plan(
                subject(LocalDate.of(1950, 3, 1)),
                TODAY,
                new CoachRunConditions(20, false, null, FitnessFactor.BALANCE, false),
                TODAY,
                "시험",
                List.of());

        assertThat(result).isNotNull();
        List<CoachRunResult.Session> sessions = sessionsOf(result);
        assertThat(sessions).isNotEmpty().allSatisfy(it -> assertThat(it.video())
                .isNotNull());
        assertThat(sessions).anySatisfy(it -> assertThat(it.fitnessFactor()).isEqualTo("평형성"));
        // 어르신 공단 영상은 V164 부터 싣지 않는다 — 본운동 칸은 성인(공통) 공단 클립이다(ExerciseClip.suits)
        List<CoachRunResult.Session> main =
                sessions.stream().filter(it -> it.phase().equals("본운동")).toList();
        assertThat(main).isNotEmpty().allSatisfy(it -> {
            CoachRunResult.Video video = it.video();
            assertThat(video).isNotNull();
            assertThat(video.source()).isEqualTo("kspo");
            assertThat(video.mediaUrl()).isEqualTo("https://openapi.kspo.or.kr/web/video/" + video.videoId() + ".mp4");
            assertThat(video.startSec()).isZero();
            assertThat(clipOf(it).ageGroup()).isEqualTo(AgeGroup.ADULT);
        });
        // 준비 · 정리 칸은 성인 유튜브 구간일 수도 있다. 어느 쪽이든 어르신에게 맞는 성인 클립이다
        assertThat(sessions).allSatisfy(it -> assertThat(clipOf(it).ageGroup()).isEqualTo(AgeGroup.ADULT));
        List<Citation> kspo = result.proposal().citations().stream()
                .filter(it -> it.chunkId().startsWith("kspo:"))
                .toList();
        // 인용 이름은 AI 표의 citation_label 그대로다(담당자 코드 형식, 끝에 「-1」 이 없다)
        assertThat(kspo).hasSizeGreaterThanOrEqualTo(main.size()).allSatisfy(it -> {
            assertThat(it.label()).matches("국민체력100 운동처방(동영상|가이드) · .*").doesNotMatch(".*[-－]\\s*\\d+\\s*$");
            assertThat(it.url()).startsWith("https://openapi.kspo.or.kr/web/video/");
        });
    }

    @Test
    @DisplayName("청소년 편성은 「공통」 공단 영상 클립도 후보로 쓴다 — V165 부터 「공통」 은 청소년 줄도 있다")
    void 청소년_편성은_공통_공단_영상도_쓴다() {
        CoachRunResult result = planner.plan(
                subject(LocalDate.of(2011, 3, 1)),
                TODAY,
                new CoachRunConditions(20, false, null, FitnessFactor.CARDIO, false),
                TODAY,
                "시험",
                List.of());

        assertThat(result).isNotNull();
        List<CoachRunResult.Session> main = sessionsOf(result).stream()
                .filter(it -> it.phase().equals("본운동"))
                .toList();
        assertThat(main).isNotEmpty().allSatisfy(it -> assertThat(it.fitnessFactor())
                .isEqualTo("심폐지구력"));
        // 클립 행의 연령대가 성인(첫 줄)인 공단 클립 = 「공통」 영상이 청소년 본운동에 들어온다
        assertThat(main).anySatisfy(it -> {
            assertThat(it.video().isKspo()).isTrue();
            assertThat(clipOf(it).ageGroup()).isEqualTo(AgeGroup.ADULT);
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
                "시험",
                List.of());

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
                "시험",
                List.of());

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

    /** runDate 하루를 짜고 칸 영상 구간(videoId-startSec)을 차례대로 돌려준다. recent 는 최근 받은 영상 id(최근 것부터). */
    private List<String> dayOf(ProfileDetails child, LocalDate runDate, FitnessFactor factor, List<String> recent) {
        CoachRunResult result = planner.plan(
                child, runDate, new CoachRunConditions(20, false, null, factor, false), TODAY, "시험", recent);
        assertThat(result).isNotNull();
        return sessionsOf(result).stream()
                .map(it -> it.video().videoId() + "-" + it.video().startSec())
                .toList();
    }

    /** 2주 동안 날마다 짠다. withRecent 면 앞 14일 동안 받은 영상 id 를 최근 것부터 넘긴다(BE 가 AI 에 보내는 recent_video_ids 와 같다). */
    private List<List<String>> twoWeeks(ProfileDetails child, FitnessFactor factor, boolean withRecent) {
        List<List<String>> days = new ArrayList<>();
        for (int day = 0; day < 14; day++) {
            List<String> recent = new ArrayList<>();
            if (withRecent) {
                Set<String> seen = new LinkedHashSet<>();
                for (int back = days.size() - 1; back >= 0; back--) {
                    days.get(back).forEach(it -> seen.add(it.substring(0, it.lastIndexOf('-'))));
                }
                recent.addAll(seen);
            }
            days.add(dayOf(child, TODAY.plusDays(day), factor, recent));
        }
        return days;
    }

    @Test
    @DisplayName("2주 동안 날마다 짜도 이틀 연속 같은 칸 묶음이 아니고, 최근 받은 영상을 넘기면 서로 다른 클립이 늘어난다 — QA 에서 2주 49칸에 서로 다른 클립이 6개뿐이었다")
    void 이주_동안_날마다_다른_묶음을_짠다() {
        ProfileDetails child = subject(LocalDate.of(2016, 5, 1));

        List<List<String>> before = twoWeeks(child, FitnessFactor.CARDIO, false);
        List<List<String>> after = twoWeeks(child, FitnessFactor.CARDIO, true);

        for (int day = 1; day < after.size(); day++) {
            assertThat(after.get(day)).as("%d일째와 그 전날", day).isNotEqualTo(after.get(day - 1));
        }
        Set<String> distinctBefore = new HashSet<>();
        before.forEach(distinctBefore::addAll);
        Set<String> distinctAfter = new HashSet<>();
        after.forEach(distinctAfter::addAll);
        assertThat(distinctAfter).hasSizeGreaterThan(distinctBefore.size()).hasSizeGreaterThan(12);
    }

    @Test
    @DisplayName("최근 받은 영상이 없어도 같은 순위끼리는 날짜를 시드로 삼아 섞어, 2주 동안 한 가지 묶음만 나오지는 않는다")
    void 최근_영상이_없어도_날마다_섞는다() {
        List<List<String>> days = twoWeeks(subject(LocalDate.of(2016, 5, 1)), FitnessFactor.CARDIO, false);

        assertThat(new HashSet<>(days)).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("미션 제목의 요인과 첫 본운동 칸(대표 영상)의 요인이 같다 — 「심폐지구력 키우기」 인데 대표 영상이 근력 영상이었다")
    void 첫_본운동_칸은_키울_요인_클립이다() {
        for (FitnessFactor factor : List.of(FitnessFactor.CARDIO, FitnessFactor.STRENGTH, FitnessFactor.FLEXIBILITY)) {
            CoachRunResult result = planner.plan(
                    subject(LocalDate.of(2016, 5, 1)),
                    TODAY,
                    new CoachRunConditions(20, false, null, factor, false),
                    TODAY,
                    "시험",
                    List.of());
            assertThat(result).isNotNull();
            assertThat(result.proposal().missions().getFirst().title()).startsWith(factor.getLabel() + " 키우기");
            assertThat(sessionsOf(result))
                    .filteredOn(it -> it.phase().equals("본운동"))
                    .first()
                    .satisfies(it -> assertThat(it.fitnessFactor()).isEqualTo(factor.getLabel()));
        }
    }
}
