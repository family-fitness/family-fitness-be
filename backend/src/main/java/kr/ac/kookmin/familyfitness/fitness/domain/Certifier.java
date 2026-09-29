package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ac.kookmin.familyfitness.fitness.domain.Certification.MissingItem;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeTable.Judgement;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 한 회차의 국민체력100 인증 등급을 매긴다 — 인증서처럼 한 사람에게 하나(AI `stats/tables.py` certify 와 같은 규칙).
 * 기준표가 고정이라 같은 회차에는 언제나 같은 등급이 나와서 저장하지 않고 읽을 때 셈한다.
 * <ul>
 *   <li>판정 값은 잰 항목 + 체지방률(003) · 허리둘레(004) + 키 · 몸무게로 셈한 BMI(018, 소수 둘째 자리) · 허리둘레-신장비
 *       (042, 소수 셋째 자리)다. AI `stats/assess.py` 의 `_with_body` 와 같다.
 *   <li>나이는 유아기만 개월, 나머지는 만 나이로 기준 줄과 등급 비율 칸을 고른다.
 * </ul>
 */
public class Certifier {
    public static final Certifier EMPTY = new Certifier(GradeTable.EMPTY, GradeDistribution.EMPTY);

    static final String BMI = "018";
    static final String WAIST_TO_HEIGHT = "042";

    private final GradeTable thresholds;
    private final GradeDistribution distribution;

    public Certifier(GradeTable thresholds, GradeDistribution distribution) {
        this.thresholds = thresholds;
        this.distribution = distribution;
    }

    public GradeTable getThresholds() {
        return thresholds;
    }

    public GradeDistribution getDistribution() {
        return distribution;
    }

    /** 저장된 회차의 인증 등급. 성별은 프로필에서, 개월 나이는 측정일 기준으로 부르는 쪽이 준다(유아기만 쓴다). */
    public Certification certify(FitnessTest test, Sex sex, int ageMonthsAtTest) {
        AgeGroup ageGroup = test.getAgeGroup();
        int age = ageGroup == AgeGroup.TODDLER ? ageMonthsAtTest : test.getAgeAtTest();
        return certify(ageGroup, sex, age, test.getMeasurements(), test.getBody());
    }

    /**
     * @param age 유아기는 개월, 나머지는 만 나이
     * @param measurements 잰 항목 코드 → 값. 신체조성은 {@code body} 로 따로 받는다
     */
    public Certification certify(
            AgeGroup ageGroup, Sex sex, int age, Map<String, BigDecimal> measurements, BodyMeasures body) {
        Map<String, BigDecimal> values = valuesOf(measurements, body);
        Judgement judgement = thresholds.judge(ageGroup, sex, age, values);
        List<Certification.PeerShare> peers = distribution.of(ageGroup, sex, age);
        if (!judgement.hasCriteria()) {
            return new Certification(null, CertificationStatus.NO_CRITERIA, List.of(), peers);
        }
        boolean noHeight = body.heightCm() == null;
        if (judgement.grade() == null) {
            List<MissingItem> fewest = null;
            for (Grade grade : GradeTable.CERTIFIED) {
                Set<String> codes = judgement.missing().get(grade);
                if (codes == null) continue;
                List<MissingItem> items = describe(codes, ageGroup, noHeight);
                // 모자란 것이 가장 적은 등급 — 같으면 먼저 본(높은) 등급을 둔다
                if (fewest == null || items.size() < fewest.size()) fewest = items;
            }
            return new Certification(null, CertificationStatus.NEEDS_ITEMS, fewest == null ? List.of() : fewest, peers);
        }
        Set<String> firstMissing = judgement.missing().get(Grade.FIRST);
        List<MissingItem> toFirst = firstMissing == null ? List.of() : describe(firstMissing, ageGroup, noHeight);
        return new Certification(judgement.grade(), CertificationStatus.GRADED, toFirst, peers);
    }

    /** AI `_with_body` — 잰 값에 체지방률 · 허리둘레를 더하고, 키 · 몸무게로 BMI, 허리둘레 · 키로 허리둘레-신장비를 셈한다. */
    static Map<String, BigDecimal> valuesOf(Map<String, BigDecimal> measurements, BodyMeasures body) {
        Map<String, BigDecimal> values = new LinkedHashMap<>(measurements);
        if (body.bodyFatPct() != null) values.put(BodyMeasures.BODY_FAT_CODE, body.bodyFatPct());
        if (body.waistCm() != null) values.put(BodyMeasures.WAIST_CODE, body.waistCm());
        BigDecimal height = body.heightCm();
        BigDecimal weight = body.weightKg();
        if (!values.containsKey(BMI) && isPresent(height) && isPresent(weight)) {
            // 파이썬과 같은 double 셈 뒤 round(x, 2) — 이진수 값 그대로 짝수 쪽 반올림
            double metres = height.doubleValue() / 100;
            values.put(BMI, rounded(weight.doubleValue() / (metres * metres), 2));
        }
        BigDecimal waist = values.get(BodyMeasures.WAIST_CODE);
        if (!values.containsKey(WAIST_TO_HEIGHT) && waist != null && isPresent(height)) {
            values.put(WAIST_TO_HEIGHT, rounded(waist.doubleValue() / height.doubleValue(), 3));
        }
        return values;
    }

    /** 파이썬 `if profile.height_cm` 처럼 null 과 0 을 빈 값으로 본다. */
    private static boolean isPresent(@Nullable BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static BigDecimal rounded(double value, int scale) {
        return new BigDecimal(value).setScale(scale, RoundingMode.HALF_EVEN);
    }

    /** 모자란 항목 코드 → 사람이 한 번에 재는 것 단위. 첫 코드 차례. */
    static List<MissingItem> describe(Set<String> codes, AgeGroup ageGroup, boolean noHeight) {
        List<MissingItem> items = new ArrayList<>();
        Set<String> alternatives = new TreeSet<>(codes);
        alternatives.retainAll(GradeTable.ALTERNATIVES);
        if (!alternatives.isEmpty()) {
            items.add(new MissingItem(
                    List.copyOf(alternatives),
                    String.join(
                            " 또는 ",
                            alternatives.stream()
                                    .map(it -> labelOf(it, ageGroup))
                                    .toList())));
        }
        for (String code : new TreeSet<>(codes)) {
            if (GradeTable.ALTERNATIVES.contains(code)) continue;
            String label =
                    switch (code) {
                        case BMI -> "키 · 몸무게";
                        case WAIST_TO_HEIGHT -> noHeight ? "키 · 허리둘레" : "허리둘레";
                        case BodyMeasures.BODY_FAT_CODE -> "체지방률";
                        case BodyMeasures.WAIST_CODE -> "허리둘레";
                        default -> labelOf(code, ageGroup);
                    };
            items.add(new MissingItem(List.of(code), label));
        }
        items.sort(Comparator.comparing(it -> it.itemCodes().getFirst()));
        return items;
    }

    private static String labelOf(String code, AgeGroup ageGroup) {
        FitnessItem item = FitnessItem.findByCode(code);
        return item == null ? code : item.label(ageGroup);
    }
}
