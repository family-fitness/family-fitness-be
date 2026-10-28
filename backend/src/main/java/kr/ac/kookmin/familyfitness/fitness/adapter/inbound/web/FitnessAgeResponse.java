package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessAgeService;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessAgeTable;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessItem;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import org.jspecify.annotations.Nullable;

/**
 * 성인 체력 나이 응답. {@code status} 가 OK 일 때만 {@code fitnessAge} · {@code ageGap} 이 있다.
 * {@code ageGap} 은 체력 나이 - 측정일 기준 만 나이(음수면 실제보다 젊다).
 * {@code items[].itemAge} 는 소수 첫째 자리로 반올림한 항목 나이, {@code bound} 는 표의 끝(19 · 64세)에 걸렸는지다.
 */
public record FitnessAgeResponse(
        FitnessAgeService.Status status,
        @Nullable LocalDate testedOn,
        @Nullable Integer age,
        @Nullable Integer fitnessAge,
        @Nullable Integer ageGap,
        int minItems,
        Range range,
        List<Item> items,
        List<Excluded> excludedItems) {

    public record Range(int min, int max) {}

    public record Item(
            String itemCode,
            String itemName,
            String unit,
            BigDecimal value,
            BigDecimal itemAge,
            FitnessAgeTable.Bound bound) {}

    /** 잰 항목 가운데 나이와 관계가 약해 뺀 항목. reason 은 지금 NOT_AGE_RELATED 하나다. */
    public record Excluded(String itemCode, String itemName, String reason) {}

    public static FitnessAgeResponse of(FitnessAgeService.View view) {
        FitnessTest test = view.test();
        FitnessAgeTable.Estimate estimate = view.estimate();
        Integer fitnessAge = estimate.fitnessAge();
        Integer age = test == null ? null : view.ageAtTest();
        Integer gap = fitnessAge != null && age != null ? fitnessAge - age : null;
        List<Item> items = estimate.items().stream()
                .map(a -> new Item(
                        a.item().getCode(),
                        a.item().getItemName(),
                        a.item().getUnit(),
                        a.value(),
                        BigDecimal.valueOf(a.age()).setScale(1, RoundingMode.HALF_UP),
                        a.bound()))
                .toList();
        List<Excluded> excluded =
                estimate.excluded().stream().map(FitnessAgeResponse::excluded).toList();
        return new FitnessAgeResponse(
                view.status(),
                test == null ? null : test.getTestedOn(),
                age,
                fitnessAge,
                gap,
                FitnessAgeTable.MIN_ITEMS,
                new Range(FitnessAgeTable.MIN_AGE, FitnessAgeTable.MAX_AGE),
                items,
                excluded);
    }

    private static Excluded excluded(FitnessItem item) {
        return new Excluded(item.getCode(), item.getItemName(), "NOT_AGE_RELATED");
    }
}
