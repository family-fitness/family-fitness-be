package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherOutlook;
import org.jspecify.annotations.Nullable;

/**
 * 하루 낮 날씨 예보. 기상청 단기예보(공공데이터포털)를 읽는 어댑터가 구현한다.
 * 위치를 모르면(위도 · 경도가 null) 어댑터의 기본 위치(서울시청)로 본다.
 * 날씨를 알 수 없으면(인증키 없음 · 조회 실패 · 예보 범위 밖) null 이고, 그 경우 편성은 날씨 없이 그대로 간다.
 */
@FunctionalInterface
public interface WeatherForecast {
    WeatherForecast NONE = (latitude, longitude, date) -> null;

    @Nullable
    WeatherOutlook outlook(@Nullable Double latitude, @Nullable Double longitude, LocalDate date);
}
