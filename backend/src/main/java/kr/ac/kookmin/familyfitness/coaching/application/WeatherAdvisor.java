package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.coaching.application.port.WeatherForecast;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherAdvice;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherOutlook;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 편성을 시작할 때 그날 날씨를 보고 조건을 고친다({@link WeatherAdvice}). 집(HOME)을 이미 골랐으면 예보를 부르지 않는다.
 * 예보를 읽다 무엇이 잘못돼도 편성은 막지 않는다 — 날씨 없이 그대로 짠다.
 */
@Component
public class WeatherAdvisor {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** 예보를 보지 않는 조언자(시험 · 옛 생성자용). */
    static final WeatherAdvisor NONE = new WeatherAdvisor(WeatherForecast.NONE);

    private final WeatherForecast forecast;

    public WeatherAdvisor(WeatherForecast forecast) {
        this.forecast = forecast;
    }

    public WeatherAdvice advise(
            CoachRunConditions conditions,
            LocalDate date,
            LocalDate today,
            @Nullable Double latitude,
            @Nullable Double longitude) {
        if (conditions.place() == CoachPlace.HOME) return new WeatherAdvice(conditions, null);
        WeatherOutlook outlook = null;
        try {
            outlook = forecast.outlook(latitude, longitude, date);
        } catch (RuntimeException e) {
            log.warn("날씨 예보를 읽지 못해 날씨 없이 짠다: date={} ({})", date, e.toString());
        }
        return WeatherAdvice.of(conditions, outlook, today);
    }
}
