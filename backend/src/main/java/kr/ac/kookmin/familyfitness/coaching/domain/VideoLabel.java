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

    /** 안전 필터: 라벨 연령 범위와 교차하는 영상만. 라벨 없는 영상은 아이 연령대에 나가지 않는다. */
    public boolean suitableFor(AgeGroup ageGroup) {
        if (ageFrom == null || ageTo == null) return !AgeRange.isChildGroup(ageGroup);
        return AgeRange.of(ageGroup).intersects(ageFrom, ageTo);
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
