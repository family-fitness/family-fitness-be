package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 부팅 시 메모리에 올리는 또래 분포 표. 측정값 → 백분위를 AI `stats/tables.py` 의 peer() · percentile_of() 와 한 줄씩 같게 낸다
 * (계약 §0 「백분위 계산」). 불변이라 교체는 통째로 바꾼다.
 * <ul>
 *   <li>칸은 (연령대, 성별, 나이, 항목)이다. 연령대는 만 나이로 정하고, 나이는 유아기만 개월 · 나머지는 만 나이로 본다.
 *   <li>표본이 {@link #MIN_SAMPLE} 에 못 미치거나 칸이 없으면(만 7~10세 등) null.
 *   <li>백분위 = 값의 아래쪽 자리(같은 값 앞)와 위쪽 자리(같은 값 뒤)의 가운데. 같은 값이 몰린 항목(044 벽패스의 0회 등)에서
 *       순위가 튀지 않게 하려는 AI 규칙이다. 낮을수록 좋은 항목은 100 에서 뺀다. 짝수 쪽으로 반올림한 뒤(파이썬 round)
 *       0~100 으로 자른다 — 0 과 100 도 나온다.
 * </ul>
 */
public class PeerTable {
    /** 이보다 적은 표본은 백분위를 내지 않는다(AI stats/tables.py MIN_SAMPLE). */
    public static final int MIN_SAMPLE = 30;

    public static final PeerTable EMPTY = new PeerTable(Map.of());

    private final Map<Key, Peer> peers;

    private PeerTable(Map<Key, Peer> peers) {
        this.peers = peers;
    }

    private record Key(AgeGroup ageGroup, Sex sex, int age, String itemCode) {}

    private record Peer(int n, double[] quantiles) {}

    public int getSize() {
        return peers.size();
    }

    /** 측정값의 백분위(0~100). 그 사람 · 항목의 또래 분포가 없거나 표본이 모자라면 null. */
    public @Nullable Integer percentile(FitnessItem item, Sex sex, int ageYears, int ageMonths, BigDecimal value) {
        AgeGroup ageGroup = AgeGroup.ofAge(ageYears);
        int age = ageGroup == AgeGroup.TODDLER ? ageMonths : ageYears;
        Peer peer = peers.get(new Key(ageGroup, sex, age, item.getCode()));
        if (peer == null || peer.n() < MIN_SAMPLE) return null;
        return percentileOf(peer.quantiles(), value.doubleValue(), !item.isHigherIsBetter());
    }

    /** AI percentile_of 를 옮긴 것. {@code quantiles} 는 오름차순 101칸. */
    static int percentileOf(double[] quantiles, double value, boolean lowerIsBetter) {
        int low = searchLeft(quantiles, value);
        int high = searchRight(quantiles, value);
        double rank = (low + high) / 2.0;
        if (lowerIsBetter) rank = 100 - rank;
        // 파이썬 round 는 짝수 쪽 반올림이다(42.5 → 42). Math.rint 가 같다.
        return (int) Math.rint(Math.min(100.0, Math.max(0.0, rank)));
    }

    /** np.searchsorted(side="left") — {@code value} 이상인 첫 자리. */
    private static int searchLeft(double[] sorted, double value) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] < value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** np.searchsorted(side="right") — {@code value} 보다 큰 첫 자리. */
    private static int searchRight(double[] sorted, double value) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** 같은 (연령대, 성별, 나이, 항목)이 두 줄이면 어느 쪽을 쓸지 몰라 받지 않는다. */
    public static PeerTable of(Collection<PeerQuantiles> rows) {
        Map<Key, Peer> byKey = new LinkedHashMap<>();
        for (PeerQuantiles row : rows) {
            Key key = new Key(row.ageGroup(), row.sex(), row.age(), row.itemCode());
            double[] quantiles =
                    row.quantiles().stream().mapToDouble(Double::doubleValue).toArray();
            if (byKey.put(key, new Peer(row.n(), quantiles)) != null) {
                throw new IllegalArgumentException("또래 분포 칸이 두 줄이다: " + key);
            }
        }
        return new PeerTable(Map.copyOf(byKey));
    }
}
