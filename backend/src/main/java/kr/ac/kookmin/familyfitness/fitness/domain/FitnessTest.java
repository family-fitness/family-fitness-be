package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 측정 회차 애그리거트. 항목과 함께 통째로 저장되고, 저장 뒤에는 바뀌지 않는다.
 * 규칙: 항목 0개 저장 안 함 · 005/006 거부 · 카탈로그 밖 코드 거부 · 연령대 항목만 허용 · 한 요청에 같은 항목 두 번 금지.
 */
public class FitnessTest {
    public static final int STRENGTHEN_ABOVE = 75;

    private final UUID id;
    private final UUID profileId;
    private final LocalDate testedOn;
    private final FitnessTestSource source;

    /** testedOn 기준 만 나이 */
    private final int ageAtTest;

    private final @Nullable BigDecimal heightCm;
    private final @Nullable BigDecimal weightKg;
    private final List<FitnessTestItem> items;
    private final Instant createdAt;
    private final AgeGroup ageGroup;

    private FitnessTest(
            UUID id,
            UUID profileId,
            LocalDate testedOn,
            FitnessTestSource source,
            int ageAtTest,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            List<FitnessTestItem> items,
            Instant createdAt) {
        this.id = id;
        this.profileId = profileId;
        this.testedOn = testedOn;
        this.source = source;
        this.ageAtTest = ageAtTest;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.items = items;
        this.createdAt = createdAt;
        this.ageGroup = AgeGroup.ofAge(ageAtTest);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public LocalDate getTestedOn() {
        return testedOn;
    }

    public FitnessTestSource getSource() {
        return source;
    }

    public int getAgeAtTest() {
        return ageAtTest;
    }

    public @Nullable BigDecimal getHeightCm() {
        return heightCm;
    }

    public @Nullable BigDecimal getWeightKg() {
        return weightKg;
    }

    public List<FitnessTestItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AgeGroup getAgeGroup() {
        return ageGroup;
    }

    /** itemCode → 원시값. 005·006 은 애초에 없다. */
    public Map<String, BigDecimal> getMeasurements() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (FitnessTestItem item : items) {
            values.put(item.item().getCode(), item.value());
        }
        return values;
    }

    private List<FitnessTestItem> scoredItems() {
        return items.stream().filter(it -> it.percentile() != null).toList();
    }

    /** 레이더 6요인({@link FitnessFactor#RADAR}). 요인에 항목이 여럿이면 백분위 평균(반올림), 하나도 없으면 null. */
    public List<RadarPoint> radar() {
        return FitnessFactor.RADAR.stream()
                .map(factor -> {
                    List<Integer> percentiles = scoredItems().stream()
                            .filter(it -> it.item().getFactor() == factor)
                            .map(FitnessTestItem::percentile)
                            .toList();
                    return new RadarPoint(factor, percentiles.isEmpty() ? null : roundedAverage(percentiles));
                })
                .toList();
    }

    public @Nullable FactorPoint getWeakest() {
        return scoredItems().stream()
                .min(Comparator.comparingInt(it -> it.percentile()))
                .map(FitnessTest::toFactorPoint)
                .orElse(null);
    }

    public @Nullable FactorPoint getStrongest() {
        return scoredItems().stream()
                .max(Comparator.comparingInt(it -> it.percentile()))
                .map(FitnessTest::toFactorPoint)
                .orElse(null);
    }

    /** 측정 항목 백분위의 평균(반올림, 1~99). 규준이 붙은 항목이 없으면 null. 가족 체력 지도 카드의 한 줄 요약에 쓴다. */
    public @Nullable Integer getOverallPercentile() {
        List<Integer> percentiles =
                scoredItems().stream().map(FitnessTestItem::percentile).toList();
        if (percentiles.isEmpty()) return null;
        return Math.min(99, Math.max(1, roundedAverage(percentiles)));
    }

    public CoachDirection getCoachDirection() {
        FactorPoint weakest = getWeakest();
        int percentile = weakest == null ? 0 : weakest.percentile();
        return percentile > STRENGTHEN_ABOVE ? CoachDirection.STRENGTHEN : CoachDirection.GROWTH;
    }

    private static FactorPoint toFactorPoint(FitnessTestItem item) {
        return new FactorPoint(item.item().getFactor(), item.item().getCode(), item.percentile());
    }

    private static int roundedAverage(List<Integer> percentiles) {
        double average =
                percentiles.stream().mapToInt(Integer::intValue).average().orElseThrow();
        return (int) Math.round(average);
    }

    /**
     * 새 측정 회차. {@code scorer} 가 (항목, 값) → 백분위(규준 없으면 null) 를 돌려주고, 그 결과가 저장 시점 값으로 굳는다.
     */
    public static FitnessTest register(
            UUID id,
            UUID profileId,
            LocalDate testedOn,
            FitnessTestSource source,
            int ageAtTest,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            List<Measurement> measurements,
            BiFunction<FitnessItem, BigDecimal, @Nullable Integer> scorer,
            Instant createdAt) {
        if (measurements.isEmpty()) throw new NoItemsException();
        AgeGroup ageGroup = AgeGroup.ofAge(ageAtTest);
        Set<FitnessItem> seen = new LinkedHashSet<>();
        List<FitnessTestItem> items = measurements.stream()
                .map(m -> {
                    FitnessItem item = FitnessItem.resolve(m.itemCode());
                    if (!seen.add(item)) throw new DuplicateItemException(item.getCode());
                    if (!item.isFor(ageGroup)) throw new ItemNotForAgeGroupException(item.getCode(), ageGroup);
                    return new FitnessTestItem(item, m.value(), ItemScore.ofPercentile(scorer.apply(item, m.value())));
                })
                .toList();
        return new FitnessTest(id, profileId, testedOn, source, ageAtTest, heightCm, weightKg, items, createdAt);
    }

    /** 저장소에서 복원. 굳어 있는 백분위에서 등급·구간을 다시 파생한다(계산은 {@link ItemScore} 한 곳). */
    public static FitnessTest reconstitute(
            UUID id,
            UUID profileId,
            LocalDate testedOn,
            FitnessTestSource source,
            int ageAtTest,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            List<StoredItem> items,
            Instant createdAt) {
        return new FitnessTest(
                id,
                profileId,
                testedOn,
                source,
                ageAtTest,
                heightCm,
                weightKg,
                items.stream()
                        .map(it -> {
                            FitnessItem item = FitnessItem.findByCode(it.itemCode());
                            if (item == null) throw new UnknownItemException(it.itemCode());
                            return new FitnessTestItem(item, it.value(), ItemScore.ofPercentile(it.percentile()));
                        })
                        .toList(),
                createdAt);
    }

    public record StoredItem(
            String itemCode, BigDecimal value, @Nullable Integer percentile) {}
}
