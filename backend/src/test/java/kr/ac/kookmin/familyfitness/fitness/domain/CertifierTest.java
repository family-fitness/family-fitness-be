package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.fitness.domain.Certification.MissingItem;
import kr.ac.kookmin.familyfitness.fitness.domain.Certification.PeerShare;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인증 등급 = AI `stats/tables.py` certify(한 사람에게 하나). 아래 기준값은 AI `data/release/grade_thresholds.csv` 에서 그대로
 * 옮겼다. AI 와 같은지는 AiCertifyParityTest 가 AI 가 낸 기대값 전부로 따로 본다. 여기서는 서버가 더 주는 것(status ·
 * missingItems · peers)과 규칙의 모서리를 본다.
 */
class CertifierTest {
    private static final List<GradeThreshold> ROWS = new ArrayList<>();

    static {
        // 유소년 여 11세
        youthGirl(Grade.FIRST, "020", ">=", 62, "028", ">=", 44.4, "009", ">=", 36, "012", ">=", 10.9);
        youthGirl(Grade.FIRST, "043", ">=", 32, "022", ">=", 165, "044", ">=", 19);
        youthGirl(Grade.SECOND, "020", ">=", 51, "028", ">=", 39.5, "009", ">=", 26, "012", ">=", 6.5);
        youthGirl(Grade.SECOND, "043", ">=", 30, "022", ">=", 146, "044", ">=", 13);
        youthGirl(Grade.THIRD, "020", ">=", 40, "028", ">=", 34.8, "009", ">=", 18, "012", ">=", 3.0);
        youthGirl(Grade.THIRD, "018", "<", 23.3, "042", "<", 0.47);
        // 성인 남 40~44세 — 035 · 037 은 둘 중 하나, 3등급은 BMI · 체지방률이 사이 값(between)
        adultMan(Grade.FIRST, "020", 42, "035", 40.9, "037", 40.9, "028", 62.8, "019", 44, "012", 14.2);
        adultMan(Grade.FIRST, "021", 11.0, "040", 0.32, "022", 209, "041", 0.547);
        adultMan(Grade.SECOND, "020", 35, "035", 38.8, "037", 38.8, "028", 57.2, "019", 38, "012", 9.5);
        adultMan(Grade.SECOND, "021", 12.1, "040", 0.346, "022", 195, "041", 0.521);
        adultMan(Grade.THIRD, "020", 27, "035", 36.6, "037", 36.6, "028", 51.6, "019", 32, "012", 4.8);
        ROWS.add(row(AgeGroup.ADULT, Sex.M, NormAgeUnit.YEARS, 40, 44, Grade.THIRD, "018", "between", 18.5, 25.0));
        ROWS.add(row(AgeGroup.ADULT, Sex.M, NormAgeUnit.YEARS, 40, 44, Grade.THIRD, "003", "between", 7.0, 27.0));
        // 유아기 남 48~53개월(일부)
        ROWS.add(row(AgeGroup.TODDLER, Sex.M, NormAgeUnit.MONTHS, 48, 53, Grade.FIRST, "020", ">=", 48, null));
        ROWS.add(row(AgeGroup.TODDLER, Sex.M, NormAgeUnit.MONTHS, 48, 53, Grade.SECOND, "020", ">=", 35, null));
    }

    private static void youthGirl(Grade grade, Object... cells) {
        for (int i = 0; i < cells.length; i += 3) {
            ROWS.add(row(
                    AgeGroup.YOUTH,
                    Sex.F,
                    NormAgeUnit.YEARS,
                    11,
                    11,
                    grade,
                    (String) cells[i],
                    (String) cells[i + 1],
                    ((Number) cells[i + 2]).doubleValue(),
                    null));
        }
    }

    private static void adultMan(Grade grade, Object... cells) {
        for (int i = 0; i < cells.length; i += 2) {
            String code = (String) cells[i];
            String op = code.equals("021") || code.equals("040") ? "<=" : ">=";
            ROWS.add(row(
                    AgeGroup.ADULT,
                    Sex.M,
                    NormAgeUnit.YEARS,
                    40,
                    44,
                    grade,
                    code,
                    op,
                    ((Number) cells[i + 1]).doubleValue(),
                    null));
        }
    }

    private static GradeThreshold row(
            AgeGroup ageGroup,
            Sex sex,
            NormAgeUnit unit,
            int from,
            int to,
            Grade grade,
            String itemCode,
            String op,
            double cutoff,
            @Nullable Double upper) {
        return new GradeThreshold(
                ageGroup,
                sex,
                unit,
                from,
                to,
                grade,
                itemCode,
                ThresholdOp.of(op),
                BigDecimal.valueOf(cutoff),
                upper == null ? null : BigDecimal.valueOf(upper));
    }

    private static final List<GradeDistribution.Row> PEERS = List.of(
            // 표의 차례가 뒤섞여 있어도 1등급 · 2등급 · 3등급 · 참가 차례로 준다
            new GradeDistribution.Row(AgeGroup.YOUTH, Sex.F, 11, Grade.PARTICIPATION, new BigDecimal("0.6413")),
            new GradeDistribution.Row(AgeGroup.YOUTH, Sex.F, 11, Grade.FIRST, new BigDecimal("0.0322")),
            new GradeDistribution.Row(AgeGroup.YOUTH, Sex.F, 11, Grade.SECOND, new BigDecimal("0.0983")),
            new GradeDistribution.Row(AgeGroup.YOUTH, Sex.F, 11, Grade.THIRD, new BigDecimal("0.2282")));

    private final Certifier certifier = new Certifier(GradeTable.of(ROWS), GradeDistribution.of(PEERS));

    private static Map<String, BigDecimal> values(String... pairs) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String pair : pairs) {
            String[] cell = pair.split("=");
            out.put(cell[0], new BigDecimal(cell[1]));
        }
        return out;
    }

    private static BodyMeasures body(
            @Nullable String height, @Nullable String weight, @Nullable String fat, @Nullable String waist) {
        return new BodyMeasures(
                height == null ? null : new BigDecimal(height),
                weight == null ? null : new BigDecimal(weight),
                fat == null ? null : new BigDecimal(fat),
                waist == null ? null : new BigDecimal(waist));
    }

    private Certification girl11(BodyMeasures body, String... pairs) {
        return certifier.certify(AgeGroup.YOUTH, Sex.F, 11, values(pairs), body);
    }

    private static final String[] GIRL_ALL_FIRST = {
        "020=62", "028=44.4", "009=36", "012=10.9", "043=32", "022=165", "044=19"
    };

    @Test
    @DisplayName("1등급 줄의 항목을 다 재고 다 넘으면 1등급 — 기준값 그대로도 넘는다(>=)")
    void 다_재고_다_넘으면_1등급() {
        Certification result = girl11(BodyMeasures.NONE, GIRL_ALL_FIRST);
        assertThat(result.grade()).isEqualTo(Grade.FIRST);
        assertThat(result.status()).isEqualTo(CertificationStatus.GRADED);
        assertThat(result.missingItems()).isEmpty();
    }

    @Test
    @DisplayName("한 항목이라도 못 미치면 다음 등급으로 — 1 · 2등급을 다 못 넘고 3등급 줄은 신체조성이 없어 못 보면 참가")
    void 못_미치면_다음_등급_아무_등급도_못_넘으면_참가() {
        Certification second =
                girl11(BodyMeasures.NONE, "020=62", "028=44.3", "009=36", "012=10.9", "043=32", "022=165", "044=19");
        assertThat(second.grade()).isEqualTo(Grade.SECOND);

        Certification participation =
                girl11(BodyMeasures.NONE, "020=50", "028=44.4", "009=36", "012=10.9", "043=32", "022=165", "044=19");
        assertThat(participation.grade()).isEqualTo(Grade.PARTICIPATION);
        assertThat(participation.status()).isEqualTo(CertificationStatus.GRADED);
        assertThat(participation.missingItems()).isEmpty();
    }

    @Test
    @DisplayName("3등급은 BMI(키 · 몸무게) · 허리둘레-신장비(허리둘레 · 키)를 서버가 셈해 본다 — 1등급 줄은 못 봐서 1등급에 모자란 것을 준다")
    void 등급_3_은_BMI_와_허리둘레_신장비를_셈해_본다() {
        // BMI 38 / 1.45² = 18.07 < 23.3, WHtR 60 / 145 = 0.414 < 0.47
        Certification third = girl11(body("145", "38", null, "60"), "020=70", "028=41.3", "009=20", "012=4.0");
        assertThat(third.grade()).isEqualTo(Grade.THIRD);
        assertThat(third.status()).isEqualTo(CertificationStatus.GRADED);
        assertThat(third.missingItems())
                .containsExactly(
                        new MissingItem(List.of("022"), "제자리멀리뛰기"),
                        new MissingItem(List.of("043"), "반복옆뛰기"),
                        new MissingItem(List.of("044"), "눈-손협응력(벽패스)"));

        // 허리둘레-신장비 70 / 145 = 0.483 ≥ 0.47 이면 3등급을 못 넘는다 — 3등급만 판정했으니 참가
        Certification over = girl11(body("145", "38", null, "70"), "020=70", "028=41.3", "009=20", "012=4.0");
        assertThat(over.grade()).isEqualTo(Grade.PARTICIPATION);
    }

    @Test
    @DisplayName("어느 등급도 판정하지 못하면 NEEDS_ITEMS — 모자란 것이 가장 적은 등급의 것을 사람이 재는 단위로 준다")
    void 판정하지_못하면_모자란_것이_가장_적은_등급의_것을_준다() {
        Certification result = girl11(body("145", "38", null, null), "020=70", "028=41.3", "012=4.0");
        assertThat(result.grade()).isNull();
        assertThat(result.status()).isEqualTo(CertificationStatus.NEEDS_ITEMS);
        // 1 · 2등급은 009 · 043 · 022 · 044 넷, 3등급은 009 와 허리둘레 둘
        assertThat(result.missingItems())
                .containsExactly(new MissingItem(List.of("009"), "윗몸말아올리기"), new MissingItem(List.of("042"), "허리둘레"));

        // 키도 없으면 BMI 는 「키 · 몸무게」, 허리둘레-신장비는 「키 · 허리둘레」 — 3등급 셋이 1등급 넷보다 적다
        Certification noBody = girl11(BodyMeasures.NONE, "020=70", "028=41.3", "012=4.0");
        assertThat(noBody.missingItems())
                .containsExactly(
                        new MissingItem(List.of("009"), "윗몸말아올리기"),
                        new MissingItem(List.of("018"), "키와 몸무게"),
                        new MissingItem(List.of("042"), "키와 허리둘레"));
        // 1등급(022 · 044)과 3등급(BMI · 허리둘레-신장비)이 둘씩 같으면 높은 등급의 것
        Certification tie = girl11(BodyMeasures.NONE, "020=70", "028=41.3", "012=4.0", "009=20", "043=33");
        assertThat(tie.missingItems())
                .containsExactly(
                        new MissingItem(List.of("022"), "제자리멀리뛰기"), new MissingItem(List.of("044"), "눈-손협응력(벽패스)"));
    }

    @Test
    @DisplayName("035 · 037 은 둘 중 하나만 재면 되고, 둘 다 안 쟀으면 한 칸(「또는」)으로 알린다 — 둘 다 쟀으면 둘 다 넘어야 한다")
    void 심폐지구력_두_시험은_하나만_재면_된다() {
        String[] first = {"020=42", "028=62.8", "019=44", "012=14.2", "021=11.0", "040=0.32", "022=209", "041=0.547"};
        List<String> withTreadmill = new ArrayList<>(List.of(first));
        withTreadmill.add("035=41");
        Certification treadmill = certifier.certify(
                AgeGroup.ADULT, Sex.M, 42, values(withTreadmill.toArray(String[]::new)), BodyMeasures.NONE);
        assertThat(treadmill.grade()).isEqualTo(Grade.FIRST);

        List<String> both = new ArrayList<>(withTreadmill);
        both.add("037=30");
        assertThat(certifier
                        .certify(AgeGroup.ADULT, Sex.M, 42, values(both.toArray(String[]::new)), BodyMeasures.NONE)
                        .grade())
                .isEqualTo(Grade.PARTICIPATION);

        Certification neither = certifier.certify(AgeGroup.ADULT, Sex.M, 42, values(first), BodyMeasures.NONE);
        assertThat(neither.status()).isEqualTo(CertificationStatus.NEEDS_ITEMS);
        assertThat(neither.missingItems())
                .containsExactly(new MissingItem(List.of("035", "037"), "트레드밀VO2max 또는 스텝검사VO2max"));
    }

    @Test
    @DisplayName("성인 3등급은 BMI · 체지방률이 사이 값 안이어야 한다 — 양 끝 포함, 체지방률이 없으면 3등급을 못 본다")
    void 성인_3등급은_BMI_와_체지방률이_사이_값_안이어야_한다() {
        String[] third = {"020=27", "035=36.6", "028=51.6", "019=32", "012=4.8"};
        // 키 170 · 몸무게 72.3 → BMI 25.02 > 25.0 → 3등급만 판정했는데 못 넘어서 참가
        assertThat(certifier
                        .certify(AgeGroup.ADULT, Sex.M, 40, values(third), body("170", "72.3", "27.0", null))
                        .grade())
                .isEqualTo(Grade.PARTICIPATION);
        // 몸무게 72.2 → BMI 24.98, 체지방률 27.0(윗끝) → 3등급
        Certification graded =
                certifier.certify(AgeGroup.ADULT, Sex.M, 40, values(third), body("170", "72.2", "27.0", null));
        assertThat(graded.grade()).isEqualTo(Grade.THIRD);
        // 체지방률이 없으면 3등급도 판정하지 못한다
        Certification noFat =
                certifier.certify(AgeGroup.ADULT, Sex.M, 40, values(third), body("170", "72.2", null, null));
        assertThat(noFat.status()).isEqualTo(CertificationStatus.NEEDS_ITEMS);
        assertThat(noFat.missingItems()).containsExactly(new MissingItem(List.of("003"), "체지방률"));
    }

    @Test
    @DisplayName("BMI 는 파이썬처럼 double 로 셈해 소수 둘째 자리, 허리둘레-신장비는 셋째 자리에서 짝수 쪽으로 반올림한다")
    void BMI_와_허리둘레_신장비는_파이썬처럼_셈한다() {
        Map<String, BigDecimal> values = Certifier.valuesOf(Map.of(), body("145", "38", "20.5", "68.2"));
        assertThat(values.get("018")).isEqualByComparingTo("18.07");
        assertThat(values.get("042")).isEqualByComparingTo("0.470");
        assertThat(values.get("003")).isEqualByComparingTo("20.5");
        assertThat(values.get("004")).isEqualByComparingTo("68.2");
        // 키가 없으면 BMI · 허리둘레-신장비는 없다. 허리둘레는 그대로 있다
        assertThat(Certifier.valuesOf(Map.of(), body(null, "38", null, "68.2"))).containsOnlyKeys("004");
    }

    @Test
    @DisplayName("기준 줄이 하나도 없으면 NO_CRITERIA — 만 7~10세 · 어르신 · 유아기 47개월")
    void 기준_줄이_없으면_NO_CRITERIA() {
        for (Certification result : List.of(
                certifier.certify(AgeGroup.YOUTH, Sex.F, 9, values("020=70"), BodyMeasures.NONE),
                certifier.certify(AgeGroup.SENIOR, Sex.M, 70, values("012=10"), BodyMeasures.NONE),
                certifier.certify(AgeGroup.TODDLER, Sex.M, 47, values("020=50"), BodyMeasures.NONE))) {
            assertThat(result.grade()).isNull();
            assertThat(result.status()).isEqualTo(CertificationStatus.NO_CRITERIA);
            assertThat(result.missingItems()).isEmpty();
        }
    }

    @Test
    @DisplayName("유아기는 개월로 기준 줄을 고른다 — 저장된 회차는 측정일 개월 나이로 셈한다")
    void 유아기는_개월로_기준_줄을_고른다() {
        FitnessTest toddler = FitnessTest.register(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.time.LocalDate.of(2026, 9, 1),
                FitnessTestSource.SELF_INPUT,
                4,
                BodyMeasures.NONE,
                List.of(new Measurement("020", new BigDecimal("50"))),
                (item, value) -> null,
                java.time.Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(certifier.certify(toddler, Sex.M, 50).grade()).isEqualTo(Grade.FIRST);
        assertThat(certifier.certify(toddler, Sex.M, 54).status()).isEqualTo(CertificationStatus.NO_CRITERIA);
    }

    @Test
    @DisplayName("또래 등급 비율은 그 연령대 · 성별 · 나이의 네 줄을 1등급 · 2등급 · 3등급 · 참가 차례로, 없으면 빈 목록")
    void 또래_등급_비율은_네_줄을_차례로_준다() {
        assertThat(girl11(BodyMeasures.NONE, GIRL_ALL_FIRST).peers())
                .containsExactly(
                        new PeerShare(Grade.FIRST, new BigDecimal("0.0322")),
                        new PeerShare(Grade.SECOND, new BigDecimal("0.0983")),
                        new PeerShare(Grade.THIRD, new BigDecimal("0.2282")),
                        new PeerShare(Grade.PARTICIPATION, new BigDecimal("0.6413")));
        assertThat(certifier
                        .certify(AgeGroup.YOUTH, Sex.M, 11, values("020=70"), BodyMeasures.NONE)
                        .peers())
                .isEmpty();
    }
}
