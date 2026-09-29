package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;

/**
 * 항목 점수 묶음. 구간 · 「상위 n%」 문구는 백분위에서 여기 한 곳에서만 파생한다. 또래 분포가 없으면 셋 다 null 이다.
 * 등급은 항목마다 매기지 않는다 — 한 사람에게 하나를 {@link Certifier} 가 매긴다.
 */
public record ItemScore(
        @Nullable Integer percentile,
        @Nullable Band band,
        @Nullable String topPercentText) {
    public static final ItemScore NONE = new ItemScore(null, null, null);

    public static ItemScore of(@Nullable Integer percentile) {
        if (percentile == null) return NONE;
        return new ItemScore(percentile, Band.ofPercentile(percentile), Copy.topPercentText(percentile));
    }
}
