package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherOutlook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KmaForecastParserTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("격자 좌표: 서울시청은 (60, 127), 부산시청은 (98, 76), 제주시청은 (53, 38)이다")
    void 격자_좌표() {
        assertThat(KmaGrid.of(37.5665, 126.9780)).isEqualTo(new KmaGrid(60, 127));
        assertThat(KmaGrid.of(35.1798, 129.0750)).isEqualTo(new KmaGrid(98, 76));
        assertThat(KmaGrid.of(33.4996, 126.5312)).isEqualTo(new KmaGrid(53, 38));
    }

    @Test
    @DisplayName("발표 시각: 발표 뒤 15분이 지난 가장 최근 시각(02 · 05 · … · 23시). 02:15 전이면 전날 23시다")
    void 발표_시각() {
        LocalDate day = LocalDate.of(2026, 11, 4);
        assertThat(KmaForecastParser.baseOf(day.atTime(5, 14))).isEqualTo(day.atTime(2, 0));
        assertThat(KmaForecastParser.baseOf(day.atTime(5, 15))).isEqualTo(day.atTime(5, 0));
        assertThat(KmaForecastParser.baseOf(day.atTime(10, 59))).isEqualTo(day.atTime(8, 0));
        assertThat(KmaForecastParser.baseOf(day.atTime(23, 40))).isEqualTo(day.atTime(23, 0));
        assertThat(KmaForecastParser.baseOf(day.atTime(2, 14)))
                .isEqualTo(day.minusDays(1).atTime(23, 0));
        assertThat(KmaForecastParser.baseOf(day.atTime(0, 5)))
                .isEqualTo(day.minusDays(1).atTime(23, 0));
        assertThat(KmaForecastParser.baseOf(LocalDateTime.of(2026, 11, 4, 2, 15)))
                .isEqualTo(day.atTime(2, 0));
    }

    @Test
    @DisplayName("낮(07~20시) 강수확률의 최댓값과 처음 나온 강수형태를 고르고, 밤 시간과 다른 항목은 보지 않는다")
    void 낮_시간만_본다() {
        String body = KmaSamples.body(
                KmaSamples.item("POP", "20261104", "0600", "90"),
                KmaSamples.item("PTY", "20261104", "0600", "3"),
                KmaSamples.item("TMP", "20261104", "0700", "9"),
                KmaSamples.item("POP", "20261104", "0700", "20"),
                KmaSamples.item("PTY", "20261104", "0700", "0"),
                KmaSamples.item("POP", "20261104", "1400", "70"),
                KmaSamples.item("PTY", "20261104", "1400", "4"),
                KmaSamples.item("POP", "20261104", "1500", "60"),
                KmaSamples.item("PTY", "20261104", "1500", "1"),
                KmaSamples.item("POP", "20261104", "2000", "30"),
                KmaSamples.item("POP", "20261104", "2100", "100"),
                KmaSamples.item("POP", "20261105", "0700", "10"),
                KmaSamples.item("PTY", "20261105", "0700", "0"),
                KmaSamples.item("POP", "20261105", "2000", "20"),
                KmaSamples.item("PTY", "20261105", "2000", "0"));

        Map<LocalDate, WeatherOutlook> days = KmaForecastParser.parse(JSON, body);

        LocalDate d4 = LocalDate.of(2026, 11, 4);
        LocalDate d5 = LocalDate.of(2026, 11, 5);
        assertThat(days)
                .containsOnlyKeys(d4, d5)
                .containsEntry(d4, new WeatherOutlook(d4, 70, WeatherOutlook.Precipitation.SHOWER))
                .containsEntry(d5, new WeatherOutlook(d5, 20, null));
    }

    @Test
    @DisplayName("20시 예보까지 받지 못한 날(응답 끝에서 잘린 날)은 남기지 않는다")
    void 잘린_날은_남기지_않는다() {
        String body = KmaSamples.body(
                KmaSamples.item("POP", "20261104", "2000", "10"),
                KmaSamples.item("POP", "20261107", "0700", "80"),
                KmaSamples.item("PTY", "20261107", "0700", "1"));

        assertThat(KmaForecastParser.parse(JSON, body)).containsOnlyKeys(LocalDate.of(2026, 11, 4));
    }

    @Test
    @DisplayName("결과 코드가 00 이 아니거나(자료 없음 등) JSON 이 아니면(인증키 오류는 XML) 예외다")
    void 오류_응답은_예외() {
        String noData = "{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NO_DATA\"}}}";
        String keyError = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                + "<returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";

        IllegalStateException e1 =
                assertThrows(IllegalStateException.class, () -> KmaForecastParser.parse(JSON, noData));
        IllegalStateException e2 =
                assertThrows(IllegalStateException.class, () -> KmaForecastParser.parse(JSON, keyError));

        assertThat(e1.getMessage()).contains("03 NO_DATA");
        assertThat(e2.getMessage()).contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }
}
