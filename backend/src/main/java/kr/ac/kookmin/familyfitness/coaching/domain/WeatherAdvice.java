package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 날씨에 맞춘 편성 조건. 그날 낮에 비 · 눈 소식이 있을 때만 바꾼다.
 * <ul>
 *   <li>장소를 고르지 않았으면(place = null) 집(HOME)으로 짜고 까닭을 남긴다.
 *   <li>밖(OUTDOOR)을 골랐으면 보호자의 선택을 그대로 두고 주의만 남긴다.
 *   <li>집(HOME)을 골랐거나 비 소식이 없거나 날씨를 모르면 아무것도 바꾸지 않는다.
 * </ul>
 *
 * @param note 실행 응답의 notices 맨 앞에 싣는 안내. 바꾼 것이 없으면 null
 */
public record WeatherAdvice(
        CoachRunConditions conditions, @Nullable String note) {

    public static WeatherAdvice of(CoachRunConditions conditions, @Nullable WeatherOutlook outlook, LocalDate today) {
        if (outlook == null || !outlook.wet() || conditions.place() == CoachPlace.HOME) {
            return new WeatherAdvice(conditions, null);
        }
        String when = outlook.date().equals(today)
                ? "오늘"
                : outlook.date().getMonthValue() + "월 " + outlook.date().getDayOfMonth() + "일";
        String forecast = when + " " + outlook.label() + " 예보(강수확률 " + outlook.maxPrecipitationProbability() + "%)가 있어";
        if (conditions.place() == CoachPlace.OUTDOOR) {
            return new WeatherAdvice(conditions, forecast + "요. 밖에서 운동한다면 날씨를 한 번 더 확인해 주세요.");
        }
        CoachRunConditions home = new CoachRunConditions(
                conditions.minutes(),
                conditions.quiet(),
                CoachPlace.HOME,
                conditions.focusFactor(),
                conditions.withParent(),
                conditions.heightCm(),
                conditions.weightKg());
        return new WeatherAdvice(home, forecast + " 집에서 하는 운동으로 짰어요.");
    }
}
