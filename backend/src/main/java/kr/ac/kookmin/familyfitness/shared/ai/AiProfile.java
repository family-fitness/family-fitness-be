package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** 요청 프로필. 이름·생년월일·계정 식별자를 담지 않는다. */
public record AiProfile(
        String profileRef,
        /* 유아기는 개월, 그 외는 세 */
        int age,
        String ageUnit,
        String sex,
        @Nullable Double heightCm,
        @Nullable Double weightKg,
        /* itemCode → 값. 005·006 제외. */
        Map<String, Double> measurements) {
    public String inputLevel() {
        if (!measurements.isEmpty()) return "L2";
        if (heightCm != null && weightKg != null) return "L1";
        return "L0";
    }
}
