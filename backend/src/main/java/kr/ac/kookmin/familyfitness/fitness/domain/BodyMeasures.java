package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * 측정 회차에 같이 적은 몸 값. 넷 다 고를 수 있고, 안 적었으면 null 이다(프로필 값으로 채우지 않는다).
 * 점수(백분위)는 내지 않고, 인증 등급 3등급 판정의 신체조성 관문(BMI · 체지방률 · 허리둘레-신장비)에 쓴다.
 * 체지방률은 AI 항목 003, 허리둘레는 004 다.
 */
public record BodyMeasures(
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg,
        @Nullable BigDecimal bodyFatPct,
        @Nullable BigDecimal waistCm) {
    public static final BodyMeasures NONE = new BodyMeasures(null, null, null, null);

    public static final String BODY_FAT_CODE = "003";
    public static final String WAIST_CODE = "004";
}
