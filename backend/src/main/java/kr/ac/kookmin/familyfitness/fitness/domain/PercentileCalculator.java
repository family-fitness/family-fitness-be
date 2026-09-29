package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 측정값 → 백분위 (계약 §0 「백분위·등급 계산」).
 * <ul>
 *   <li>(항목, 성별, 나이) 구간의 백분위 포인트 사이를 선형 보간하고 표 밖은 끝점으로 자른다.
 *   <li>값이 여러 포인트와 같으면(같은 값이 몰린 항목 — 044 벽패스의 0회 등) 그 포인트들 백분위의 가운데다. AI
 *       `stats/tables.py` percentile_of 가 아래쪽 자리와 위쪽 자리의 가운데를 쓰는 것과 같은 생각이다.
 *   <li>↓(낮을수록 좋은) 항목은 부호를 뒤집어 계산한다 — 값이 작을수록 백분위가 높다.
 *   <li>결과는 정수 1~99 로 자른다(0·100 금지). 규준이 없으면 null.
 * </ul>
 */
public class PercentileCalculator {
    public static final int MIN = 1;
    public static final int MAX = 99;

    private final NormTable table;

    public PercentileCalculator(NormTable table) {
        this.table = table;
    }

    public @Nullable Integer percentile(FitnessItem item, Sex sex, int age, double value) {
        return percentile(item, sex, age, value, age * 12);
    }

    public @Nullable Integer percentile(FitnessItem item, Sex sex, int age, double value, int ageMonths) {
        NormBucket bucket = table.bucket(item.getCode(), sex, age, ageMonths);
        if (bucket == null) return null;
        if (bucket.points().isEmpty()) return null;
        double raw = interpolate(bucket.points(), value, item.isHigherIsBetter());
        return Math.min(MAX, Math.max(MIN, (int) Math.round(raw)));
    }

    private double interpolate(List<NormBucket.Point> points, double value, boolean higherIsBetter) {
        double sign = higherIsBetter ? 1.0 : -1.0;
        double x = value * sign;
        int last = points.size() - 1;
        double firstP = points.getFirst().percentile();
        double firstV = points.getFirst().value() * sign;
        double lastP = points.get(last).percentile();
        double lastV = points.get(last).value() * sign;
        @Nullable Double tied = middleOfTies(points, x, sign);
        if (tied != null) return tied;
        if (x <= firstV) return firstP;
        if (x >= lastV) return lastP;
        for (int i = 0; i < last; i++) {
            double p0 = points.get(i).percentile();
            double v0 = points.get(i).value() * sign;
            double p1 = points.get(i + 1).percentile();
            double v1 = points.get(i + 1).value() * sign;
            if (x >= v0 && x <= v1) {
                if (v1 == v0) return p0;
                return p0 + (x - v0) / (v1 - v0) * (p1 - p0);
            }
        }
        return lastP;
    }

    /** {@code x} 와 같은 값의 포인트가 둘 이상이면 그 백분위 범위의 가운데. 포인트는 백분위 오름차순이라 같은 값은 이어져 있다. */
    private static @Nullable Double middleOfTies(List<NormBucket.Point> points, double x, double sign) {
        Integer first = null;
        Integer last = null;
        for (NormBucket.Point point : points) {
            if (point.value() * sign != x) continue;
            if (first == null) first = point.percentile();
            last = point.percentile();
        }
        if (first == null || last == null || first.equals(last)) return null;
        return (first + last) / 2.0;
    }
}
