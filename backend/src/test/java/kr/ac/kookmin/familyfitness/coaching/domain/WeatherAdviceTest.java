package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherAdviceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private static CoachRunConditions conditions(CoachPlace place) {
        return new CoachRunConditions(20, false, place, FitnessFactor.AGILITY, true);
    }

    private static WeatherOutlook rain(LocalDate date) {
        return new WeatherOutlook(date, 70, WeatherOutlook.Precipitation.RAIN);
    }

    @Test
    @DisplayName("장소를 고르지 않았는데 비 소식이 있으면 집 운동으로 바꾸고 까닭을 남긴다. 다른 조건은 그대로다")
    void 장소를_고르지_않았으면_집으로_바꾼다() {
        WeatherAdvice advice = WeatherAdvice.of(conditions(null), rain(TODAY), TODAY);

        assertThat(advice.conditions().place()).isEqualTo(CoachPlace.HOME);
        assertThat(advice.conditions().minutes()).isEqualTo(20);
        assertThat(advice.conditions().focusFactor()).isEqualTo(FitnessFactor.AGILITY);
        assertThat(advice.conditions().withParent()).isTrue();
        assertThat(advice.note()).isEqualTo("오늘 비 예보(강수확률 70%)가 있어 집에서 하는 운동으로 짰어요.");
    }

    @Test
    @DisplayName("밖을 골랐으면 보호자의 선택을 그대로 두고 주의만 남긴다. 오늘이 아니면 날짜로 적는다")
    void 밖을_골랐으면_주의만_남긴다() {
        LocalDate tomorrow = TODAY.plusDays(1);
        WeatherOutlook snow = new WeatherOutlook(tomorrow, 40, WeatherOutlook.Precipitation.SNOW);

        WeatherAdvice advice = WeatherAdvice.of(conditions(CoachPlace.OUTDOOR), snow, TODAY);

        assertThat(advice.conditions()).isEqualTo(conditions(CoachPlace.OUTDOOR));
        assertThat(advice.note()).isEqualTo("11월 5일 눈 예보(강수확률 40%)가 있어요. 밖에서 운동한다면 날씨를 한 번 더 확인해 주세요.");
    }

    @Test
    @DisplayName("집을 골랐거나, 비 소식이 없거나, 날씨를 모르면 아무것도 바꾸지 않는다")
    void 바꿀_까닭이_없으면_그대로다() {
        WeatherOutlook clear = new WeatherOutlook(TODAY, 30, null);

        assertThat(WeatherAdvice.of(conditions(CoachPlace.HOME), rain(TODAY), TODAY))
                .isEqualTo(new WeatherAdvice(conditions(CoachPlace.HOME), null));
        assertThat(WeatherAdvice.of(conditions(null), clear, TODAY))
                .isEqualTo(new WeatherAdvice(conditions(null), null));
        assertThat(WeatherAdvice.of(conditions(null), null, TODAY))
                .isEqualTo(new WeatherAdvice(conditions(null), null));
    }

    @Test
    @DisplayName("강수형태가 없어도 강수확률이 60% 이상이면 비 소식으로 본다. 59% 는 아니다")
    void 강수확률_60_이상이면_젖은_날() {
        assertThat(new WeatherOutlook(TODAY, 60, null).wet()).isTrue();
        assertThat(new WeatherOutlook(TODAY, 59, null).wet()).isFalse();
        assertThat(new WeatherOutlook(TODAY, 60, null).label()).isEqualTo("비");
        assertThat(new WeatherOutlook(TODAY, 10, WeatherOutlook.Precipitation.SHOWER).wet())
                .isTrue();
    }

    @Test
    @DisplayName("강수형태 코드 1~4 만 이름이 있고 0 이나 모르는 값은 null, 강수확률은 0~100 이다")
    void 강수형태_코드와_강수확률_범위() {
        assertThat(WeatherOutlook.Precipitation.ofCode(1)).isEqualTo(WeatherOutlook.Precipitation.RAIN);
        assertThat(WeatherOutlook.Precipitation.ofCode(2)).isEqualTo(WeatherOutlook.Precipitation.RAIN_SNOW);
        assertThat(WeatherOutlook.Precipitation.ofCode(4)).isEqualTo(WeatherOutlook.Precipitation.SHOWER);
        assertThat(WeatherOutlook.Precipitation.ofCode(0)).isNull();
        assertThat(WeatherOutlook.Precipitation.ofCode(7)).isNull();
        assertThrows(IllegalArgumentException.class, () -> new WeatherOutlook(TODAY, 101, null));
    }
}
