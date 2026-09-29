package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;

/**
 * 항목 점수 묶음. 구간 · 「상위 n%」 문구는 백분위에서 여기 한 곳에서만 파생하고, 등급은 공식 기준표({@link GradeTable})가
 * 낸 값을 그대로 든다 — 둘은 따로다. 규준이 없으면 백분위 · 구간 · 문구가 null, 기준 줄이 없으면 등급이 null 이다.
 */
public record ItemScore(
        @Nullable Integer percentile,
        @Nullable Grade grade,
        @Nullable Band band,
        @Nullable String topPercentText) {
    public static final ItemScore NONE = new ItemScore(null, null, null, null);

    public static ItemScore of(@Nullable Integer percentile, @Nullable Grade grade) {
        if (percentile == null) return new ItemScore(null, grade, null, null);
        return new ItemScore(percentile, grade, Band.ofPercentile(percentile), Copy.topPercentText(percentile));
    }
}
