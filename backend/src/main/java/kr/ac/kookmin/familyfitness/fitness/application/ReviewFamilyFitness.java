package kr.ac.kookmin.familyfitness.fitness.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.domain.BodyMeasures;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyCreated;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyDays;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 심사용 체험 가족의 측정을 넣는다. 아이 둘은 한 달 전({@link ReviewFamilyDays#FIRST_TEST_DAY})과 사흘 전 두 번 재서, 다시 잰
 * 기록과 조금 나아진 값이 보인다. 엄마와 아빠는 {@link ReviewFamilyDays#PARENT_TEST_DAY} 에 한 번 쟀다. identity 가
 * {@link ReviewFamilyCreated} 를 발행한 트랜잭션 안에서 동기로 듣는다. 로그인 응답이 나가기 전에 측정과 등급이 서 있어야 해서다.
 * 여기서 실패하면 계정과 가족까지 같이 되돌린다.
 *
 * <p>측정은 보호자가 화면에서 넣는 것과 같은 {@link FitnessTestService#register} 를 거친다(권한, 동의, 항목, 값 범위 검사 그대로).
 * 두 번째 회차를 넣으면 다시 재기 경험치와 업적 「다시 측정」 이 저절로 따라온다(progress 가 같은 이벤트를 듣는다).
 * <ul>
 *   <li>하윤(만 11세 여, 유소년): 유소년 종목 일곱 가지와 키, 몸무게, 허리둘레. 사흘 전 값은 국민체력100 기준표(V154, AI
 *       grade_thresholds.csv)로 2등급 줄은 모두 넘고 1등급 줄에는 모두 조금씩 못 미치게 골랐다. 종합 등급 2등급이고, 한 등급 위를
 *       보여 줄 거리가 남는다. 기준표가 바뀌면 ReviewLoginApiTest 가 깨진다.
 *   <li>서준(만 6세 남, 유아기): 집에서 잴 만한 네 가지와 키, 몸무게. 유아기 등급은 일곱 가지를 다 재야 판정하므로 등급 없이
 *       「더 재면 판정할 수 있는 것」 이 나온다.
 * </ul>
 */
@Component
public class ReviewFamilyFitness {
    /** 아이들이 다시 잰 날은 로그인한 날의 사흘 전이다. */
    static final int MEASURED_DAYS_AGO = 3;

    /** 하윤. 뒤의 두 수는 만 11세 여 2등급, 1등급 기준(이상)이다. 일곱 가지 모두 그 사이에 있다. */
    static final Map<String, String> YOUTH = Map.of(
            "009", "30", // 윗몸말아올리기(회) 26, 36
            "012", "8.0", // 앉아윗몸앞으로굽히기(cm) 6.5, 10.9
            "020", "55", // 15m 왕복오래달리기(회) 51, 62
            "022", "150", // 제자리멀리뛰기(cm) 146, 165
            "028", "41.0", // 상대악력(%) 39.5, 44.4
            "043", "31", // 반복옆뛰기(회) 30, 32
            "044", "14"); // 눈-손협응력 벽패스(회) 13, 19

    static final BodyMeasures YOUTH_BODY =
            new BodyMeasures(new BigDecimal("148.0"), new BigDecimal("40.0"), null, new BigDecimal("62.0"));

    /** 하윤의 한 달 전 값. 사흘 전 값보다 조금씩 낮다. */
    static final Map<String, String> YOUTH_BEFORE =
            Map.of("009", "27", "012", "7.0", "020", "50", "022", "144", "028", "40.0", "043", "29", "044", "13");

    static final BodyMeasures YOUTH_BODY_BEFORE =
            new BodyMeasures(new BigDecimal("147.1"), new BigDecimal("39.4"), null, new BigDecimal("62.5"));

    /** 서준. 유아기 일곱 가지 중 넷. */
    static final Map<String, String> TODDLER = Map.of(
            "009", "9", // 윗몸말아올리기(회)
            "012", "9.0", // 앉아윗몸앞으로굽히기(cm)
            "022", "105", // 제자리멀리뛰기(cm)
            "050", "9.1"); // 5m 4회 왕복달리기(초)

    static final BodyMeasures TODDLER_BODY =
            new BodyMeasures(new BigDecimal("118.0"), new BigDecimal("21.5"), null, null);

    /** 서준의 한 달 전 값. */
    static final Map<String, String> TODDLER_BEFORE = Map.of("009", "7", "012", "8.0", "022", "98", "050", "9.6");

    static final BodyMeasures TODDLER_BODY_BEFORE =
            new BodyMeasures(new BigDecimal("117.3"), new BigDecimal("21.2"), null, null);

    /** 엄마. 앉아윗몸앞으로굽히기, 상대악력, 교차윗몸일으키기. */
    static final Map<String, String> MOM = Map.of("012", "14.0", "028", "52.0", "019", "24");

    static final BodyMeasures MOM_BODY =
            new BodyMeasures(new BigDecimal("162.0"), new BigDecimal("56.0"), null, new BigDecimal("74.0"));

    /** 아빠. 엄마 종목에 왕복오래달리기를 더했다. */
    static final Map<String, String> DAD = Map.of("012", "6.0", "028", "63.0", "019", "31", "020", "38");

    static final BodyMeasures DAD_BODY =
            new BodyMeasures(new BigDecimal("175.0"), new BigDecimal("76.0"), null, new BigDecimal("86.0"));

    private final FitnessTestService tests;
    private final ProfileQuery profiles;
    private final Clock clock;
    private final ZoneId zone;

    public ReviewFamilyFitness(FitnessTestService tests, ProfileQuery profiles, Clock clock, ZoneId zone) {
        this.tests = tests;
        this.profiles = profiles;
        this.clock = clock;
        this.zone = zone;
    }

    /** 쉬는 날 카드(activity) 다음, 운동 기록(coaching)보다 먼저 돈다. */
    @EventListener
    @Order(20)
    public void on(ReviewFamilyCreated created) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = today.minusDays(ReviewFamilyDays.FIRST_TEST_DAY);
        LocalDate again = today.minusDays(MEASURED_DAYS_AGO);
        UUID guardian = created.guardianUserId();
        tests.register(guardian, created.youthProfileId(), command(first, YOUTH_BEFORE, YOUTH_BODY_BEFORE));
        tests.register(guardian, created.toddlerProfileId(), command(first, TODDLER_BEFORE, TODDLER_BODY_BEFORE));
        tests.register(guardian, created.youthProfileId(), command(again, YOUTH, YOUTH_BODY));
        tests.register(guardian, created.toddlerProfileId(), command(again, TODDLER, TODDLER_BODY));

        LocalDate parentsOn = today.minusDays(ReviewFamilyDays.PARENT_TEST_DAY);
        for (ProfileSummary member : profiles.summariesOfFamily(created.familyId())) {
            if (!member.isParent()) continue;
            boolean mom = member.sex() == Sex.F;
            tests.register(
                    guardian, member.profileId(), command(parentsOn, mom ? MOM : DAD, mom ? MOM_BODY : DAD_BODY));
        }
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
