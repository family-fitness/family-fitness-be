package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;

/**
 * 백분위에서 파생되는 값 묶음. 등급·구간·「상위 n%」 문구는 여기 한 곳에서만 계산한다.
 * 규준이 없어 백분위가 null 이면 나머지도 전부 null 이다.
 */
public record ItemScore(
        @Nullable Integer percentile,
        @Nullable Grade grade,
        @Nullable Band band,
        @Nullable String topPercentText) {
    public static final ItemScore NONE = new ItemScore(null, null, null, null);

    public static ItemScore ofPercentile(@Nullable Integer percentile) {
        if (percentile == null) return NONE;
        return new ItemScore(
                percentile,
                Grade.ofPercentile(percentile),
                Band.ofPercentile(percentile),
                Copy.topPercentText(percentile));
    }
}
