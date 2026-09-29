package kr.ac.kookmin.familyfitness.fitness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.fitness.application.PeerCatalog;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 서버 백분위가 AI 와 같은지 못박는다. 기대값 `fitness/ai-percentiles.csv` 는 `scripts/ai_percentile_fixture.py` 가 AI 코드
 * (stats/tables.py peer · percentile_of)를 직접 불러 낸 것이고, 서버 쪽은 마이그레이션(V156)으로 올린 표를 그대로 쓴다.
 * 표의 (연령대, 성별, 나이, 항목) 칸 전부에 분위 점 위 · 사이 · 범위 밖 · 같은 값이 몰린 곳을 넣었고, 표본 30 미만 칸과 표에
 * 없는 칸도 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiPercentileParityTest {
    @Autowired
    PeerCatalog peers;

    private record Case(
            AgeGroup ageGroup,
            Sex sex,
            int age,
            String itemCode,
            String value,
            @Nullable Integer expected) {
        int ageYears() {
            return ageGroup == AgeGroup.TODDLER ? age / 12 : age;
        }

        int ageMonths() {
            return ageGroup == AgeGroup.TODDLER ? age : age * 12;
        }
    }

    private static List<Case> cases() throws IOException {
        List<Case> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(AiPercentileParityTest.class.getResourceAsStream("/fitness/ai-percentiles.csv")),
                StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertThat(header).isEqualTo("age_group,sex,age,item_code,value,percentile");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cell = line.split(",", -1);
                cases.add(new Case(
                        AgeGroup.fromLabel(cell[0]),
                        Sex.valueOf(cell[1]),
                        Integer.parseInt(cell[2]),
                        cell[3],
                        cell[4],
                        cell[5].isEmpty() ? null : Integer.valueOf(cell[5])));
            }
        }
        return cases;
    }

    @Test
    @DisplayName("AI 가 낸 백분위와 줄마다 같다 — 모든 연령대 · 성별 · 나이 · 항목 칸, 표본 30 미만과 없는 칸은 null")
    void AI_가_낸_백분위와_줄마다_같다() throws IOException {
        PeerTable table = peers.table();
        List<Case> cases = cases();
        List<String> different = new ArrayList<>();
        for (Case c : cases) {
            FitnessItem item = Objects.requireNonNull(FitnessItem.findByCode(c.itemCode()), c.itemCode());
            Integer actual = table.percentile(item, c.sex(), c.ageYears(), c.ageMonths(), new BigDecimal(c.value()));
            if (!Objects.equals(actual, c.expected())) different.add(c + " → 서버 " + actual);
        }

        assertThat(different).as("AI 와 다른 줄").isEmpty();
        // fixture 가 표 전부를 덮는다 — 표의 1,746칸에 표에 없는 칸 다섯, 연령대 다섯, 표본 30 미만 칸, 0 · 100
        Set<String> cells = cases.stream()
                .map(c -> c.ageGroup() + "/" + c.sex() + "/" + c.age() + "/" + c.itemCode())
                .collect(Collectors.toSet());
        assertThat(cells).hasSize(1746 + 5);
        assertThat(cases.stream()
                        .map(Case::ageGroup)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(AgeGroup.class))))
                .containsExactlyInAnyOrder(AgeGroup.values());
        assertThat(cases).anyMatch(c -> c.expected() == null).anyMatch(c -> Objects.equals(c.expected(), 0));
        assertThat(cases).anyMatch(c -> Objects.equals(c.expected(), 100));
        assertThat(table.getSize()).isEqualTo(1746);
    }

    @Test
    @DisplayName("낮을수록 좋은 항목은 AI common/items.py 의 lower_is_better 와 같다 — 013 · 017 · 021 · 040 · 050 · 051")
    void 낮을수록_좋은_항목은_AI_와_같다() {
        assertThat(FitnessItem.values())
                .filteredOn(it -> !it.isHigherIsBetter())
                .extracting(FitnessItem::getCode)
                .containsExactlyInAnyOrder("013", "017", "021", "040", "050", "051");
    }
}
