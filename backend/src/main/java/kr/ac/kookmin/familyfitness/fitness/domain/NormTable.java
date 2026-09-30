package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 부팅 시 메모리에 올리는 규준표. 같은 (항목, 성별, 나이 구간)에 연도가 여럿이면 최신 `source_year` 만 쓴다.
 * 불변이라 교체(refresh)는 통째로 바꾼다.
 */
public class NormTable {
    public static final NormTable EMPTY = new NormTable(Map.of());

    private final Map<Key, List<NormBucket>> buckets;

    private NormTable(Map<Key, List<NormBucket>> buckets) {
        this.buckets = buckets;
    }

    private record Key(String itemCode, Sex sex, NormAgeUnit unit) {}

    public int getSize() {
        return buckets.values().stream().mapToInt(List::size).sum();
    }

    public @Nullable NormBucket bucket(String itemCode, Sex sex, int ageYears) {
        return bucket(itemCode, sex, ageYears, ageYears * 12);
    }

    /**
     * 개월 구간(유아기)이 {@code ageMonths} 를 덮으면 그것을, 아니면 연 구간에서 {@code ageYears} 를 덮는 것을 고른다.
     * 겹치면 좁은 구간. 없으면 null.
     */
    public @Nullable NormBucket bucket(String itemCode, Sex sex, int ageYears, int ageMonths) {
        NormBucket months = bucket(new Key(itemCode, sex, NormAgeUnit.MONTHS), ageMonths);
        return months != null ? months : bucket(new Key(itemCode, sex, NormAgeUnit.YEARS), ageYears);
    }

    private @Nullable NormBucket bucket(Key key, int age) {
        List<NormBucket> candidates = buckets.get(key);
        if (candidates == null) return null;
        return candidates.stream()
                .filter(it -> it.covers(age))
                .min(Comparator.comparingInt(it -> it.ageTo() - it.ageFrom()))
                .orElse(null);
    }

    public static NormTable of(Collection<NormPoint> points) {
        Map<Key, Map<AgeRange, List<NormPoint>>> grouped = new LinkedHashMap<>();
        for (NormPoint point : points) {
            grouped.computeIfAbsent(new Key(point.itemCode(), point.sex(), point.ageUnit()), k -> new LinkedHashMap<>())
                    .computeIfAbsent(new AgeRange(point.ageFrom(), point.ageTo()), k -> new java.util.ArrayList<>())
                    .add(point);
        }
        Map<Key, List<NormBucket>> byBucket = new LinkedHashMap<>();
        grouped.forEach((key, ranges) -> byBucket.put(
                key,
                ranges.entrySet().stream()
                        .map(entry -> {
                            List<NormPoint> candidates = entry.getValue();
                            int latestYear = candidates.stream()
                                    .mapToInt(NormPoint::sourceYear)
                                    .max()
                                    .orElseThrow();
                            return new NormBucket(
                                    entry.getKey().from(),
                                    entry.getKey().to(),
                                    latestYear,
                                    candidates.stream()
                                            .filter(it -> it.sourceYear() == latestYear)
                                            .sorted(Comparator.comparingInt(NormPoint::percentile))
                                            .map(it -> new NormBucket.Point(it.percentile(), it.value()))
                                            .toList());
                        })
                        .sorted(Comparator.comparingInt(NormBucket::ageFrom))
                        .toList()));
        return new NormTable(byBucket);
    }

    private record AgeRange(int from, int to) {}
}
