package kr.ac.kookmin.familyfitness.fitness.application;

import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;

/**
 * 구성원 한 명의 카드. {@code latest} 가 null 이면 "첫 측정을 등록하면 지도가 그려져요",
 * {@link ProfileSummary#measurable()} 이 false 면 측정 버튼 없음.
 */
public record FitnessMapMember(
        ProfileSummary profile,
        /** 예: `유소년 상위 37%`. 측정이 없으면 null. */
        @Nullable String headline,
        @Nullable FitnessMapLatest latest) {}
