package kr.ac.kookmin.familyfitness.shared.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * **로컬·데모 전용** {@link AiGateway}. AI 서비스(`family-fitness-ai`)를 띄우지 않고 결정적인 가짜 응답을 돌려준다.
 * 운영에서는 `app.ai.mode=http` 로 {@link HttpAiGateway} 를 쓴다. 여기 값은 AI 인터페이스-명세 4장의 모양만 맞춘 예시이며 어떤 근거도 없다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubAiGateway implements AiGateway {
    public static final String ROLE_DRIVER = "주행자";
    /** 옛 영상 검색(searchVideos) 스텁의 영상. */
    public static final String SAMPLE_VIDEO = "IdpXx2gm90o";

    public static final int SAMPLE_VIDEO_START = 96;

    /**
     * 코치 편성 스텁의 영상 — AI 명세 4장 예시와 같은 영상이다. 준비 · 본 · 정리 클립이 다 있다.
     * 라벨 연령 7~12세(V132). 이 밖의 나이에는 영상을 붙이지 않는다.
     */
    public static final String COACH_VIDEO = "Eg3GpTv7z8s";

    public static final int SAMPLE_VIDEO_AGE_FROM = 7;
    public static final int SAMPLE_VIDEO_AGE_TO = 12;
    public static final String DAILY = "일간";
    public static final String DEFAULT_FACTOR = "유연성";
    public static final List<String> EXERCISES = List.of("다리 벌려 앞으로 상체 숙이기", "앉아서 윗몸 앞으로 굽히기", "무릎 펴고 발끝 잡기");

    /** 코치 편성 스텁이 쓰는 클립 — COACH_VIDEO 의 실제 구간(V132 video_exercises, AI 2026-09-22 판, 조용함 · 집 · 도구 없음). */
    public record SampleClip(String title, int startSec, int endSec) {
        public int durationSec() {
            return endSec - startSec;
        }
    }

    public static final List<SampleClip> WARMUP_CLIPS = List.of(
            new SampleClip("넙다리 안쪽 늘리기 (나비자세)", 144, 182),
            new SampleClip("척추 들어올리기 (고양이자세)", 188, 226),
            new SampleClip("엎드려 어깨 눌러주기 (아기자세)", 230, 270));
    public static final List<SampleClip> MAIN_CLIPS = List.of(
            new SampleClip("앉아서 상체숙여 양팔 등 뒤로 펴기", 500, 534),
            new SampleClip("손 뒤에서 깍지 끼고 가슴펴기", 536, 588),
            new SampleClip("팔꿈치 등 뒤에서 굽히고 펴기", 614, 648),
            new SampleClip("손목 잡고 팔 스트레칭", 650, 724),
            new SampleClip("상체숙여 다리 늘리기", 1104, 1150),
            new SampleClip("앉아서 상체숙여 양팔 등 뒤로 펴기", 768, 802));
    public static final List<SampleClip> COOLDOWN_CLIPS = List.of(
            new SampleClip("손목잡고 팔 늘리기", 1376, 1422),
            new SampleClip("다리 뒤 늘리기", 1426, 1466),
            new SampleClip("어깨 늘리기", 1822, 1860));
    public static final List<String> MEDICAL_WORDS = List.of("통증", "부상", "약물", "질환", "아파", "다쳤");

    private static final Map<String, String> ITEM_NAME =
            Map.of("028", "상대악력", "012", "앉아윗몸앞으로굽히기", "009", "윗몸말아올리기", "022", "제자리멀리뛰기");
    private static final Map<String, String> UNIT_OF = Map.of("028", "%", "012", "cm", "009", "회", "022", "cm");
    private static final Map<String, String> FACTOR_OF = Map.of("028", "근력", "012", "유연성", "009", "근지구력", "022", "순발력");

    private final ConcurrentHashMap<String, CoachRunRequest> runs = new ConcurrentHashMap<>();

    @Override
    public AssessmentResponse assess(AssessmentRequest request) {
        AiProfile p = request.profile();
        Map<String, String> copy = new LinkedHashMap<>();
        copy.put("strength", "꾸준히 하고 있는 영역이 많습니다.");
        copy.put("focus", "유연성은 지금 키우기 좋은 영역입니다.");
        List<AssessmentResponse.FactorScore> factors = new ArrayList<>();
        p.measurements()
                .forEach((code, value) -> factors.add(new AssessmentResponse.FactorScore(
                        FACTOR_OF.getOrDefault(code, "근력"),
                        code,
                        ITEM_NAME.getOrDefault(code, code),
                        ITEM_NAME.getOrDefault(code, code),
                        UNIT_OF.getOrDefault(code, ""),
                        value,
                        50.0,
                        50,
                        "steady",
                        120)));
        return new AssessmentResponse(
                p.inputLevel(),
                ageGroupOf(p),
                new AssessmentResponse.ChildScope(new AssessmentResponse.FocusOne("유연성", "지금 키우기 좋은 영역")),
                new AssessmentResponse.ParentScope(
                        p.measurements().isEmpty() ? null : "3등급",
                        List.of(
                                new AssessmentResponse.GradeRatio("1등급", 0.10),
                                new AssessmentResponse.GradeRatio("2등급", 0.15),
                                new AssessmentResponse.GradeRatio("3등급", 0.25),
                                new AssessmentResponse.GradeRatio("참가", 0.50)),
                        List.copyOf(factors),
                        copy),
                false,
                Copy.FITNESS_DISCLAIMER);
    }

    @Override
    public VideoSearchResponse searchVideos(VideoSearchRequest request) {
        List<String> matched = request.exerciseNames().stream().limit(1).toList();
        if (matched.isEmpty()) matched = List.of(EXERCISES.getFirst());
        return new VideoSearchResponse(
                List.of(new VideoSearchResponse.Hit(SAMPLE_VIDEO, SAMPLE_VIDEO_START, 0.82, matched, videoCitation(2))),
                0,
                0);
    }

    @Override
    public CoachRunAccepted startCoachRun(CoachRunRequest request) {
        String runId = "cr_" + UUID.randomUUID().toString().replace("-", "");
        runs.put(runId, request);
        return new CoachRunAccepted(runId, "running", 1500);
    }

    /**
     * 실제 AI 처럼 주행자마다 일간 미션 하나(start = end = period.start_date)를 낸다. 참여자는 그 주행자뿐이다.
     * 칸은 AI 가짓수 규칙({@link SessionClipCounts}, 20분이면 준비 2 · 본 4 · 정리 1)대로 준비 → 본 → 정리 차례의 클립이고,
     * duration_sec 는 클립 길이, duration_min 은 minutes_per_session 이다(칸마다 분은 AI 처럼 싣지 않는다).
     * 요인은 focus_factor, 없으면 유연성. 영상 구간은 주행자 나이가 영상 연령(7~12세) 안일 때만 붙인다.
     */
    @Override
    public CoachRunResult getCoachRun(String runId) {
        CoachRunRequest request = runs.get(runId);
        if (request == null) throw new AiRunNotFoundException(runId);
        CoachRunRequest.Constraints constraints = request.constraints();
        List<CoachRunRequest.Participant> drivers = request.profiles().stream()
                .filter(it -> it.role().equals(ROLE_DRIVER))
                .toList();
        long measured = request.profiles().stream()
                .filter(it -> !it.profile().measurements().isEmpty())
                .count();
        String factor = constraints.focusFactor() == null ? DEFAULT_FACTOR : constraints.focusFactor();
        int minutes = constraints.minutesPerSession();
        List<CoachRunResult.Step> steps = List.of(
                new CoachRunResult.Step(
                        1, "assess", "ok", "대상 " + request.profiles().size() + "명 중 측정값 있는 사람 " + measured + "명"),
                new CoachRunResult.Step(2, "retrieve", "ok", "또래 운동처방 1건·영상 1편 검색"),
                new CoachRunResult.Step(
                        3,
                        "compose",
                        "ok",
                        "주행자 " + drivers.size() + "명에게 " + factor + " 미션 " + drivers.size() + "개 편성"),
                new CoachRunResult.Step(4, "verify", "ok", "연령 필터·근거 인용 확인"));
        if (drivers.isEmpty()) {
            return new CoachRunResult(runId, "refused", steps, null, true, "no_relevant_source");
        }
        String day = LocalDate.parse(request.startDate()).toString();
        List<CoachRunResult.Mission> missions = drivers.stream()
                .map(driver -> {
                    List<CoachRunResult.Session> sessions =
                            sessions(minutes, factor, sampleVideoFits(driver.profile()));
                    return new CoachRunResult.Mission(
                            DAILY,
                            factor + " 키우기 " + minutes + "분",
                            day,
                            day,
                            List.of(new CoachRunResult.ParticipantRef(
                                    driver.profile().profileRef(), driver.role())),
                            minutes,
                            sessions.stream()
                                    .mapToInt(it -> it.durationSec() == null ? 0 : it.durationSec())
                                    .sum(),
                            sessions,
                            "오늘은 다리를 쭉 펴고 앞으로 천천히 숙여 보자!",
                            factor + "은 매일 조금씩 늘려 가는 영역입니다. 오늘 " + minutes + "분이면 충분합니다.",
                            "또래 처방에 나온 늘이는 동작을 앞세워 골랐습니다 [1].");
                })
                .toList();
        return new CoachRunResult(
                runId,
                "succeeded",
                steps,
                new CoachRunResult.Proposal(
                        missions,
                        List.of(
                                new Citation(1, "국민체력100 운동처방 · 유소년 11세", "prescription:유소년-11-F-0142", null),
                                coachVideoCitation(2)),
                        List.of()),
                false,
                null);
    }

    /** 준비 · 본 · 정리 차례로 가짓수만큼 클립을 앞에서부터 담는다. order 는 1부터 그날 전체 차례다. */
    private static List<CoachRunResult.Session> sessions(int minutes, String factor, boolean withVideo) {
        SessionClipCounts counts = SessionClipCounts.of(minutes);
        List<CoachRunResult.Session> sessions = new ArrayList<>();
        addSessions(sessions, "준비운동", WARMUP_CLIPS, counts.warmup(), factor, withVideo);
        addSessions(sessions, "본운동", MAIN_CLIPS, counts.main(), factor, withVideo);
        addSessions(sessions, "정리운동", COOLDOWN_CLIPS, counts.cooldown(), factor, withVideo);
        return List.copyOf(sessions);
    }

    private static void addSessions(
            List<CoachRunResult.Session> sessions,
            String phase,
            List<SampleClip> clips,
            int count,
            String factor,
            boolean withVideo) {
        for (int i = 0; i < count; i++) {
            SampleClip clip = clips.get(i % clips.size());
            sessions.add(new CoachRunResult.Session(
                    0,
                    phase,
                    sessions.size() + 1,
                    clip.title(),
                    factor,
                    clip.durationSec(),
                    withVideo ? new CoachRunResult.Video(COACH_VIDEO, clip.startSec(), clip.endSec()) : null,
                    List.of(1, 2)));
        }
    }

    private static boolean sampleVideoFits(AiProfile profile) {
        int years = profile.ageUnit().equals("개월") ? profile.age() / 12 : profile.age();
        return years >= SAMPLE_VIDEO_AGE_FROM && years <= SAMPLE_VIDEO_AGE_TO;
    }

    @Override
    public CoachMessageResponse ask(CoachMessageRequest request) {
        if (MEDICAL_WORDS.stream().anyMatch(it -> request.question().contains(it))) {
            return new CoachMessageResponse("", List.of(), true, "medical_query");
        }
        return new CoachMessageResponse(
                "또래 처방에서는 상체를 앞·옆으로 숙이는 준비운동 동작이 함께 제시됩니다 [1].",
                List.of(new Citation(1, "국민체력100 운동처방 · 유소년 11세", "prescription:유소년-11-F-0142", null)),
                false,
                null);
    }

    private String ageGroupOf(AiProfile p) {
        int years = p.ageUnit().equals("개월") ? p.age() / 12 : p.age();
        if (years < 7) return "유아기";
        if (years < 13) return "유소년";
        if (years < 19) return "청소년";
        if (years < 65) return "성인";
        return "어르신";
    }

    private static Citation coachVideoCitation(int index) {
        SampleClip first = WARMUP_CLIPS.getFirst();
        return new Citation(
                index,
                "국민체력100 · [유소년] 성장기 학생들을 위한 근력 운동 프로그램",
                "video:" + COACH_VIDEO,
                "https://www.youtube.com/watch?v=" + COACH_VIDEO + "&t=" + first.startSec() + "s");
    }

    private Citation videoCitation(int index) {
        return new Citation(
                index,
                "국민체력100 · 초등학생의 기초체력향상과 운동능력발달을 위한 운동",
                "video:" + SAMPLE_VIDEO,
                "https://www.youtube.com/watch?v=" + SAMPLE_VIDEO + "&t=" + SAMPLE_VIDEO_START + "s");
    }
}
