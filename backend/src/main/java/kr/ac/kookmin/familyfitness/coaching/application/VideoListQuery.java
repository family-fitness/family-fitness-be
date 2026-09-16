package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;

public record VideoListQuery(
        VideoListType list,
        @Nullable UUID profileId,
        @Nullable AgeGroup ageGroup,
        @Nullable String factor,
        @Nullable String cursor,
        int size) {}
