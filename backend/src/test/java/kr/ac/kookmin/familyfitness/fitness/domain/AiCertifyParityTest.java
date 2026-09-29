package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.fitness.application.GradeCatalog;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 서버 인증 등급이 AI 와 같은지 못박는다. 기대값 `fitness/ai-certify.csv` 는 `scripts/ai_certify_fixture.py` 가 AI 코드
 * (stats/assess.py _with_body · stats/tables.py certify)를 직접 불러 낸 것이고, 서버 쪽은 마이그레이션(V154 · V159)으로 올린
 * 표를 그대로 쓴다. 기준표에 줄이 있는 (연령대, 성별, 나이) 칸 전부에 전부 잰 사람 · 하나 빠진 사람 · 035/037 하나만 잰
 * 사람 · 3등급 신체조성 조합 · 경계값을 넣었고, 기준이 없는 나이도 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiCertifyParityTest {
    @Autowired
    GradeCatalog grades;

    private record Case(
            AgeGroup ageGroup,
            Sex sex,
            int age,
            BodyMeasures body,
            Map<String, BigDecimal> measurements,
            @Nullable String expected,
            String line) {}

    private static @Nullable BigDecimal decimal(String text) {
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    private static List<Case> cases() throws IOException {
        List<Case> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(AiCertifyParityTest.class.getResourceAsStream("/fitness/ai-certify.csv")),
                StandardCharsets.UTF_8))) {
            assertThat(reader.readLine()).isEqualTo("age_group,sex,age,height_cm,weight_kg,measurements,grade");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cell = line.split(",", -1);
                Map<String, BigDecimal> measurements = new LinkedHashMap<>();
                BigDecimal fat = null;
                BigDecimal waist = null;
                for (String pair : cell[5].split(";")) {
                    if (pair.isEmpty()) continue;
                    String[] kv = pair.split("=");
                    BigDecimal value = new BigDecimal(kv[1]);
                    switch (kv[0]) {
                        case BodyMeasures.BODY_FAT_CODE -> fat = value;
                        case BodyMeasures.WAIST_CODE -> waist = value;
                        default -> measurements.put(kv[0], value);
                    }
                }
                cases.add(new Case(
                        AgeGroup.fromLabel(cell[0]),
                        Sex.valueOf(cell[1]),
                        Integer.parseInt(cell[2]),
                        new BodyMeasures(decimal(cell[3]), decimal(cell[4]), fat, waist),
                        measurements,
                        cell[6].isEmpty() ? null : cell[6],
                        line));
            }
        }
        return cases;
    }

    @Test
    @DisplayName("AI 가 낸 인증 등급과 사람마다 같다 — status 도 등급과 맞는다(등급이 있으면 GRADED)")
    void AI_가_낸_인증_등급과_사람마다_같다() throws IOException {
        Certifier certifier = grades.certifier();
        List<Case> cases = cases();
        List<String> different = new ArrayList<>();
        for (Case c : cases) {
            Certification result = certifier.certify(c.ageGroup(), c.sex(), c.age(), c.measurements(), c.body());
            String actual = result.grade() == null ? null : result.grade().getLabel();
            boolean statusMatches = (actual != null) == (result.status() == CertificationStatus.GRADED);
            if (!Objects.equals(actual, c.expected()) || !statusMatches) {
                different.add(c.line() + " → 서버 " + actual + " " + result.status());
            }
        }

        assertThat(different).as("AI 와 다른 사람").isEmpty();
        // fixture 가 넓게 덮는다 — 연령대 다섯, 등급 넷과 미판정, 기준표 줄이 있는 칸 180개
        assertThat(cases.stream()
                        .map(Case::ageGroup)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(AgeGroup.class))))
                .containsExactlyInAnyOrder(AgeGroup.values());
        assertThat(cases.stream().map(Case::expected).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("1등급", "2등급", "3등급", "참가", null);
        assertThat(cases.stream()
                        .filter(c -> c.expected() != null)
                        .map(c -> c.ageGroup() + "/" + c.sex() + "/" + c.age())
                        .distinct())
                .hasSize(180);
        assertThat(certifier.getThresholds().getSize()).isEqualTo(1122);
        assertThat(certifier.getDistribution().getSize()).isEqualTo(1048);
    }
}
