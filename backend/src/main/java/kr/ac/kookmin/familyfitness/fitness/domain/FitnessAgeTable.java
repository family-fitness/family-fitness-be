package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 성인 체력 나이 표. 또래 분포 표(`fitness_value_quantiles` 의 성인 만 19~64세, 한 살 단위)로 「이 기록이 또래 가운데에 오는 나이」를
 * 항목마다 찾고, 그 나이들의 평균을 체력 나이로 낸다. 예측 모델 없이 공단 측정 분포만 쓴다.
 * <ul>
 *   <li>쓰는 항목: 나이가 들수록 기록이 한 방향으로 나빠지는 항목만 쓴다. 나이와 그 나이 중앙값(백분위 50)의 스피어만 순위상관이
 *       {@link #MIN_TREND} 이상이고 방향이 「나이 들수록 나빠짐」 이어야 한다(높을수록 좋은 항목은 음, 낮을수록 좋은 항목은 양).
 *       2026-10 표(V156) 기준으로 앉아윗몸앞으로굽히기(012)만 빠진다(남 -0.27, 여 -0.22). 나머지 아홉 항목은 0.91 이상이다.
 *   <li>항목 나이: 나이마다 그 기록의 백분위({@link PeerTable#percentileOf})를 구해 50 에 가장 가까운 나이를 고른다. 가장 가까운
 *       나이가 여럿이면(중앙값이 몇 해 같은 구간) 그 평균이다. 가장 젊은 나이(19세)에서도 백분위가 50 을 넘으면 19세로 두고
 *       {@link Bound#YOUNGER}, 가장 많은 나이(64세)에서도 50 에 못 미치면 64세로 두고 {@link Bound#OLDER} 를 붙인다.
 *   <li>체력 나이: 쓸 수 있는 항목이 {@link #MIN_ITEMS} 개 이상일 때만 항목 나이 평균을 반올림해 낸다.
 *   <li>표본이 {@link PeerTable#MIN_SAMPLE} 에 못 미치는 칸은 백분위처럼 쓰지 않는다.
 * </ul>
 * 불변이라 표를 다시 적재하면 통째로 바꾼다({@code PeerCatalog}).
 */
public final class FitnessAgeTable {
    public static final int MIN_AGE = 19;
    public static final int MAX_AGE = 64;

    /** 나이와 중앙값의 순위상관이 이만큼은 돼야 「나이에 따라 한 방향으로 변한다」 고 본다. */
    public static final double MIN_TREND = 0.8;

    /** 체력 나이를 내려면 쓸 수 있는 항목이 이만큼 있어야 한다. 한 항목만으로는 그 종목 실력에 가깝다. */
    public static final int MIN_ITEMS = 2;

    public static final FitnessAgeTable EMPTY = new FitnessAgeTable(Map.of(), Map.of());

    /** 항목 나이가 표의 끝에 걸렸는지. YOUNGER 는 19세보다 젊게, OLDER 는 64세보다 많게 나온 경우다. */
    public enum Bound {
        NONE,
        YOUNGER,
        OLDER
    }

    /** 한 항목의 나이. {@code age} 는 소수 첫째 자리까지 의미가 있다(같은 거리의 나이 평균). */
    public record ItemAge(FitnessItem item, BigDecimal value, double age, Bound bound) {}

    /**
     * 한 사람의 체력 나이 추정. {@code fitnessAge} 는 쓴 항목이 {@link #MIN_ITEMS} 개 미만이면 null.
     * {@code excluded} 는 잰 항목 가운데 나이와 관계가 약해 뺀 항목이다.
     */
    public record Estimate(@Nullable Integer fitnessAge, List<ItemAge> items, List<FitnessItem> excluded) {
        public Estimate {
            items = List.copyOf(items);
            excluded = List.copyOf(excluded);
        }
    }

    private record Key(Sex sex, String itemCode) {}

    private record Column(int[] ages, double[][] quantiles) {}

    /** 쓸 수 있는 (성별, 항목) 칸만. */
    private final Map<Key, Column> columns;

    /** 성인 칸 전부의 순위상관(설명과 시험용). */
    private final Map<Key, Double> trends;

    private FitnessAgeTable(Map<Key, Column> columns, Map<Key, Double> trends) {
        this.columns = columns;
        this.trends = trends;
    }

    public static FitnessAgeTable of(Collection<PeerQuantiles> rows) {
        Map<Key, List<PeerQuantiles>> byKey = new LinkedHashMap<>();
        for (PeerQuantiles row : rows) {
            if (row.ageGroup() != AgeGroup.ADULT) continue;
            if (row.age() < MIN_AGE || row.age() > MAX_AGE) continue;
            if (row.n() < PeerTable.MIN_SAMPLE) continue;
            byKey.computeIfAbsent(new Key(row.sex(), row.itemCode()), k -> new ArrayList<>())
                    .add(row);
        }
        Map<Key, Column> columns = new LinkedHashMap<>();
        Map<Key, Double> trends = new LinkedHashMap<>();
        byKey.forEach((key, list) -> {
            FitnessItem item = FitnessItem.findByCode(key.itemCode());
            if (item == null || list.size() < 2) return;
            list.sort(Comparator.comparingInt(PeerQuantiles::age));
            int[] ages = list.stream().mapToInt(PeerQuantiles::age).toArray();
            double[][] quantiles = list.stream()
                    .map(r -> r.quantiles().stream()
                            .mapToDouble(Double::doubleValue)
                            .toArray())
                    .toArray(double[][]::new);
            double[] medians = new double[ages.length];
            for (int i = 0; i < ages.length; i++) medians[i] = quantiles[i][50];
            double trend = spearman(ages, medians);
            trends.put(key, trend);
            boolean worsens = item.isHigherIsBetter() ? trend <= -MIN_TREND : trend >= MIN_TREND;
            if (worsens) columns.put(key, new Column(ages, quantiles));
        });
        return new FitnessAgeTable(Map.copyOf(columns), Map.copyOf(trends));
    }

    /** 이 성별에서 체력 나이에 쓰는 항목인지. */
    public boolean usable(Sex sex, FitnessItem item) {
        return columns.containsKey(new Key(sex, item.getCode()));
    }

    /** 이 성별에서 체력 나이에 쓰는 항목 전부. 항목 번호 차례. 측정을 더 하라고 안내할 때 쓴다. */
    public List<FitnessItem> usableItems(Sex sex) {
        List<FitnessItem> out = new ArrayList<>();
        for (FitnessItem item : FitnessItem.values()) if (usable(sex, item)) out.add(item);
        out.sort(Comparator.comparing(FitnessItem::getCode));
        return List.copyOf(out);
    }

    /** 나이와 중앙값의 순위상관. 성인 표에 없는 칸이면 null. */
    public @Nullable Double trend(Sex sex, FitnessItem item) {
        return trends.get(new Key(sex, item.getCode()));
    }

    /** 한 항목의 나이. 쓰지 않는 항목이면 null. */
    public @Nullable ItemAge itemAge(Sex sex, FitnessItem item, BigDecimal value) {
        Column column = columns.get(new Key(sex, item.getCode()));
        if (column == null) return null;
        int[] ages = column.ages();
        boolean lowerIsBetter = !item.isHigherIsBetter();
        double v = value.doubleValue();
        int[] percentiles = new int[ages.length];
        for (int i = 0; i < ages.length; i++) {
            percentiles[i] = PeerTable.percentileOf(column.quantiles()[i], v, lowerIsBetter);
        }
        if (percentiles[0] > 50) return new ItemAge(item, value, ages[0], Bound.YOUNGER);
        if (percentiles[ages.length - 1] < 50) return new ItemAge(item, value, ages[ages.length - 1], Bound.OLDER);
        int best = Integer.MAX_VALUE;
        for (int p : percentiles) best = Math.min(best, Math.abs(p - 50));
        double sum = 0;
        int count = 0;
        for (int i = 0; i < ages.length; i++) {
            if (Math.abs(percentiles[i] - 50) == best) {
                sum += ages[i];
                count++;
            }
        }
        return new ItemAge(item, value, sum / count, Bound.NONE);
    }

    /** 잰 항목들로 체력 나이를 낸다. 성인 표에 없는 항목(유소년 전용 등)은 조용히 건너뛴다. */
    public Estimate estimate(Sex sex, Map<FitnessItem, BigDecimal> measurements) {
        List<ItemAge> items = new ArrayList<>();
        List<FitnessItem> excluded = new ArrayList<>();
        measurements.forEach((item, value) -> {
            ItemAge age = itemAge(sex, item, value);
            if (age != null) items.add(age);
            else if (trend(sex, item) != null) excluded.add(item);
        });
        items.sort(Comparator.comparing(a -> a.item().getCode()));
        excluded.sort(Comparator.comparing(FitnessItem::getCode));
        Integer fitnessAge = null;
        if (items.size() >= MIN_ITEMS) {
            double mean = items.stream().mapToDouble(ItemAge::age).average().orElseThrow();
            fitnessAge = (int) Math.round(mean);
        }
        return new Estimate(fitnessAge, items, excluded);
    }

    /** 스피어만 순위상관. 같은 값은 평균 순위를 준다. 한쪽이 모두 같으면 0. */
    static double spearman(int[] xs, double[] ys) {
        double[] x = new double[xs.length];
        for (int i = 0; i < xs.length; i++) x[i] = xs[i];
        double[] rx = ranks(x);
        double[] ry = ranks(ys);
        double mx = mean(rx);
        double my = mean(ry);
        double num = 0;
        double dx = 0;
        double dy = 0;
        for (int i = 0; i < rx.length; i++) {
            num += (rx[i] - mx) * (ry[i] - my);
            dx += (rx[i] - mx) * (rx[i] - mx);
            dy += (ry[i] - my) * (ry[i] - my);
        }
        if (dx == 0 || dy == 0) return 0;
        return num / Math.sqrt(dx * dy);
    }

    private static double[] ranks(double[] values) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[values.length];
        int i = 0;
        while (i < order.length) {
            int j = i;
            while (j + 1 < order.length && values[order[j + 1]] == values[order[i]]) j++;
            double rank = (i + j) / 2.0;
            for (int k = i; k <= j; k++) ranks[order[k]] = rank;
            i = j + 1;
        }
        return ranks;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
}
