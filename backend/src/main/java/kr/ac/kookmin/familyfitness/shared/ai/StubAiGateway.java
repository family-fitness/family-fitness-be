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
 * **로컬·데모 전용** {@link AiGateway}. AI 서비스(`family-fitness-ai`)에 `/v1` 경로가 아직 없어(AI-4 이후) 결정적인 가짜 응답을 돌려준다.
 * 운영에서는 `app.ai.mode=http` 로 {@link HttpAiGateway} 를 쓴다. 여기 값은 계약 §5 의 모양만 맞춘 예시이며 어떤 근거도 없다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubAiGateway implements AiGateway {
    public static final String ROLE_DRIVER = "주행자";
    public static final String ROLE_COMPANION = "동반자";
    public static final String SAMPLE_VIDEO = "IdpXx2gm90o";
    public static final int SAMPLE_VIDEO_START = 96;
    public static final List<Integer> SESSION_OFFSETS = List.of(0, 2, 4);
    public static final List<String> EXERCISES = List.of("다리 벌려 앞으로 상체 숙이기", "앉아서 윗몸 앞으로 굽히기", "무릎 펴고 발끝 잡기");
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
    public TrajectoryResponse trajectory(TrajectoryRequest request) {
        AiProfile p = request.profile();
        int ageYears = p.ageUnit().equals("개월") ? p.age() / 12 : p.age();
        double base = p.measurements().getOrDefault(request.itemCode(), 50.0);
        List<TrajectoryResponse.Band> bands = List.of(0, 4, 8, 10).stream()
                .filter(it -> it <= request.horizonYears())
                .map(offset -> {
                    double p50 = round1(base * (1 + 0.02 * offset));
                    return new TrajectoryResponse.Band(
                            ageYears + offset, round1(p50 * 0.8), p50, round1(p50 * 1.2), 120);
                })
                .toList();
        return new TrajectoryResponse(
                "cross_sectional_group_distribution",
                request.itemCode(),
                ITEM_NAME.getOrDefault(request.itemCode(), request.itemCode()),
                UNIT_OF.getOrDefault(request.itemCode(), ""),
                bands,
                Copy.TRAJECTORY_NOTICE,
                false);
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

    @Override
    public CoachRunResult getCoachRun(String runId) {
        CoachRunRequest request = runs.get(runId);
        if (request == null) throw new AiRunNotFoundException(runId);
        List<CoachRunRequest.Participant> drivers = request.profiles().stream()
                .filter(it -> it.role().equals(ROLE_DRIVER))
                .toList();
        List<CoachRunRequest.Participant> companions = request.profiles().stream()
                .filter(it -> it.role().equals(ROLE_COMPANION))
                .toList();
        List<CoachRunRequest.Participant> subjects = drivers.isEmpty() ? companions : drivers;
        long measured = request.profiles().stream()
                .filter(it -> !it.profile().measurements().isEmpty())
                .count();
        List<CoachRunResult.Step> steps = List.of(
                new CoachRunResult.Step(
                        1, "assess", "ok", "가족 " + request.profiles().size() + "명 중 측정값 있는 구성원 " + measured + "명"),
                new CoachRunResult.Step(2, "retrieve", "ok", "또래 운동처방 1건·영상 1편 검색"),
                new CoachRunResult.Step(
                        3, "compose", "ok", "주행자 " + drivers.size() + "명에게 유연성 미션 " + subjects.size() + "개 편성"),
                new CoachRunResult.Step(4, "verify", "ok", "연령 필터·근거 인용 확인"));
        if (subjects.isEmpty()) {
            return new CoachRunResult(runId, "refused", steps, null, true, "no_relevant_source");
        }
        LocalDate start = LocalDate.parse(request.startDate());
        List<CoachRunResult.Mission> missions = subjects.stream()
                .map(subject -> {
                    List<CoachRunResult.ParticipantRef> participants = new ArrayList<>();
                    participants.add(
                            new CoachRunResult.ParticipantRef(subject.profile().profileRef(), subject.role()));
                    companions.stream()
                            .filter(it -> it != subject)
                            .forEach(it -> participants.add(new CoachRunResult.ParticipantRef(
                                    it.profile().profileRef(), it.role())));
                    List<CoachRunResult.Session> sessions = new ArrayList<>();
                    for (int i = 0; i < SESSION_OFFSETS.size(); i++) {
                        boolean withVideo = i == 0 || i == SESSION_OFFSETS.size() - 1;
                        sessions.add(new CoachRunResult.Session(
                                SESSION_OFFSETS.get(i),
                                EXERCISES.get(i),
                                "유연성",
                                request.minutesPerSession(),
                                withVideo ? new CoachRunResult.Video(SAMPLE_VIDEO, SAMPLE_VIDEO_START) : null,
                                List.of(1, 2)));
                    }
                    return new CoachRunResult.Mission(
                            "같이 늘이는 한 주",
                            start.toString(),
                            start.plusDays(6).toString(),
                            List.copyOf(participants),
                            List.copyOf(sessions),
                            "이번 주엔 다리를 쭉 펴고 앞으로 천천히 숙여 보자!",
                            "유연성은 매일 조금씩 늘려 가는 영역입니다. 한 주 " + SESSION_OFFSETS.size() + "회, 회당 "
                                    + request.minutesPerSession() + "분이면 충분합니다.");
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
                                videoCitation(2))),
                false,
                null);
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

    private Citation videoCitation(int index) {
        return new Citation(
                index,
                "국민체력100 · 초등학생의 기초체력향상과 운동능력발달을 위한 운동",
                "video:" + SAMPLE_VIDEO,
                "https://www.youtube.com/watch?v=" + SAMPLE_VIDEO + "&t=" + SAMPLE_VIDEO_START + "s");
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
