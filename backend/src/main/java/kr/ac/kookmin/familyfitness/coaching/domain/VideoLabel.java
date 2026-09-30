package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.Arrays;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;

/** 영상 라벨(AI 라벨링 결과). 값 객체. */
public record VideoLabel(
        @Nullable Integer ageFrom,
        @Nullable Integer ageTo,
        List<String> factors,
        @Nullable String intensity,
        @Nullable String space,
        @Nullable String noise,
        @Nullable String model) {
    public static final String NOISE_QUIET = "QUIET";
    public static final String SPACE_SMALL_ROOM = "SMALL_ROOM";

    /**
     * 안전 필터: 라벨 연령 범위와 교차하는 영상만. 라벨 없는 영상은 아이 연령대에 나가지 않는다. 어르신은 성인 범위와 겹치는 영상도 받는다 —
     * {@link ExerciseClip#suits} 와 같다. 공단 어르신 영상은 V164 부터 싣지 않아서, 이것이 없으면 65세 이상은 공단 영상을 하나도 받지 못한다.
     */
    public boolean suitableFor(AgeGroup ageGroup) {
        if (ageFrom == null || ageTo == null) return !AgeRange.isChildGroup(ageGroup);
        return aimsAt(ageGroup) || (ageGroup == AgeGroup.SENIOR && aimsAt(AgeGroup.ADULT));
    }

    /** 라벨 연령 범위가 그 연령대의 만 나이 범위와 겹치는지(그 연령대를 겨냥한 영상인지). 범위가 없으면 false. */
    public boolean aimsAt(AgeGroup ageGroup) {
        return ageFrom != null && ageTo != null && AgeRange.of(ageGroup).intersects(ageFrom, ageTo);
    }

    public boolean hasFactor(String factorLabel) {
        return factors.contains(factorLabel);
    }

    public static List<String> parseFactors(@Nullable String csv) {
        if (csv == null) return List.of();
        return Arrays.stream(csv.split(",", -1))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .toList();
    }
}
