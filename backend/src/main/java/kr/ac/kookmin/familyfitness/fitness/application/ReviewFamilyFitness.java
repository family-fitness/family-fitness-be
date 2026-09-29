package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.fitness.domain.BodyMeasures;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 심사용 체험 가족의 두 아이 측정을 사흘 전 날짜로 넣는다. identity 가 {@link ReviewFamilyCreated} 를 발행한 트랜잭션 안에서
 * 동기로 듣는다 — 로그인 응답이 나가기 전에 측정과 등급이 서 있어야 해서다. 여기서 실패하면 계정 · 가족까지 같이 되돌린다.
 *
 * <p>측정은 보호자가 화면에서 넣는 것과 같은 {@link FitnessTestService#register} 를 거친다(권한 · 동의 · 항목 · 값 범위 검사 그대로).
 * <ul>
 *   <li>하윤(만 11세 여, 유소년): 유소년 종목 일곱 가지 + 키 · 몸무게 · 허리둘레. 값은 국민체력100 기준표(V154 · AI
 *       grade_thresholds.csv)로 2등급 줄은 모두 넘고 1등급 줄에는 모두 조금씩 못 미치게 골랐다 — 종합 등급 2등급. 한 등급 위를 보여 줄 거리가
 *       남게. 기준표가 바뀌면 ReviewLoginApiTest 가 깨진다.
 *   <li>서준(만 6세 남, 유아기): 집에서 잴 만한 네 가지 + 키 · 몸무게. 유아기 등급은 일곱 가지를 다 재야 판정하므로 등급 없이
 *       「더 재면 판정할 수 있는 것」 이 나온다.
 * </ul>
 */
@Component
public class ReviewFamilyFitness {
    /** 체험 가족의 측정일은 로그인한 날의 사흘 전이다. */
    static final int MEASURED_DAYS_AGO = 3;

    /** 하윤 — 뒤의 두 수는 만 11세 여 2등급 · 1등급 기준(이상). 일곱 가지 모두 그 사이에 있다. */
    static final Map<String, String> YOUTH = Map.of(
            "009", "30", // 윗몸말아올리기(회) 26 · 36
            "012", "8.0", // 앉아윗몸앞으로굽히기(cm) 6.5 · 10.9
            "020", "55", // 15m 왕복오래달리기(회) 51 · 62
            "022", "150", // 제자리멀리뛰기(cm) 146 · 165
            "028", "41.0", // 상대악력(%) 39.5 · 44.4
            "043", "31", // 반복옆뛰기(회) 30 · 32
            "044", "14"); // 눈-손협응력 벽패스(회) 13 · 19

    static final BodyMeasures YOUTH_BODY =
            new BodyMeasures(new BigDecimal("148.0"), new BigDecimal("40.0"), null, new BigDecimal("62.0"));

    /** 서준 — 유아기 일곱 가지 중 넷. */
    static final Map<String, String> TODDLER = Map.of(
            "009", "9", // 윗몸말아올리기(회)
            "012", "9.0", // 앉아윗몸앞으로굽히기(cm)
            "022", "105", // 제자리멀리뛰기(cm)
            "050", "9.1"); // 5m 4회 왕복달리기(초)

    static final BodyMeasures TODDLER_BODY =
            new BodyMeasures(new BigDecimal("118.0"), new BigDecimal("21.5"), null, null);

    private final FitnessTestService tests;
    private final Clock clock;
    private final ZoneId zone;

    public ReviewFamilyFitness(FitnessTestService tests, Clock clock, ZoneId zone) {
        this.tests = tests;
        this.clock = clock;
        this.zone = zone;
    }

    @EventListener
    public void on(ReviewFamilyCreated created) {
        LocalDate measuredOn = LocalDate.now(clock.withZone(zone)).minusDays(MEASURED_DAYS_AGO);
        tests.register(created.guardianUserId(), created.youthProfileId(), command(measuredOn, YOUTH, YOUTH_BODY));
        tests.register(
                created.guardianUserId(), created.toddlerProfileId(), command(measuredOn, TODDLER, TODDLER_BODY));
    }

    private static RegisterFitnessTestCommand command(
            LocalDate measuredOn, Map<String, String> values, BodyMeasures body) {
        List<Measurement> measurements = values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(it -> new Measurement(it.getKey(), new BigDecimal(it.getValue())))
                .toList();
        return new RegisterFitnessTestCommand(measuredOn, FitnessTestSource.SELF_INPUT, body, measurements);
    }
}
