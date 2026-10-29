package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 하루의 낮(07~20시) 날씨 요약. 기상청 단기예보의 강수확률(POP) 가운데 가장 큰 값과, 강수형태(PTY)가 있었다면 그 형태를 담는다.
 *
 * @param maxPrecipitationProbability 낮 시간 강수확률의 최댓값(%, 0~100)
 * @param precipitation 낮 시간에 예보된 강수형태. 없으면 null
 */
public record WeatherOutlook(
        LocalDate date,
        int maxPrecipitationProbability,
        @Nullable Precipitation precipitation) {

    /** 이 강수확률부터는 비가 올 수 있다고 보고 밖 운동을 피한다. */
    public static final int WET_PROBABILITY = 60;

    /** 기상청 단기예보 강수형태(PTY) 1~4. 0(없음)은 null 로 둔다. */
    public enum Precipitation {
        RAIN(1, "비"),
        RAIN_SNOW(2, "비나 눈"),
        SNOW(3, "눈"),
        SHOWER(4, "소나기");

        private final int code;
        private final String label;

        Precipitation(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** PTY 값. 0 이나 모르는 값이면 null. */
        public static @Nullable Precipitation ofCode(int code) {
            for (Precipitation p : values()) if (p.code == code) return p;
            return null;
        }
    }

    public WeatherOutlook {
        if (maxPrecipitationProbability < 0 || maxPrecipitationProbability > 100) {
            throw new IllegalArgumentException("강수확률은 0~100 이어야 한다: " + maxPrecipitationProbability);
        }
    }

    /** 비 · 눈 예보가 있거나 강수확률이 {@link #WET_PROBABILITY}% 이상이면 젖은 날로 본다. */
    public boolean wet() {
        return precipitation != null || maxPrecipitationProbability >= WET_PROBABILITY;
    }

    /** 안내 문구에 쓰는 이름. 강수형태가 없으면 「비」. */
    public String label() {
        return precipitation == null ? Precipitation.RAIN.label() : precipitation.label();
    }
}
