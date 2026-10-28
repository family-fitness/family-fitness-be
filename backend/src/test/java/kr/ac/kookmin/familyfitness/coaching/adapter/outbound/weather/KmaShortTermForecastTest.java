package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherOutlook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 실제 기상청을 부르지 않고 {@link MockRestServiceServer} 로 요청 주소, 캐시, 실패 처리를 본다. */
class KmaShortTermForecastTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final String BASE_URL = "https://kma.test/VilageFcstInfoService_2.0";
    private static final String SEOUL_0500 = BASE_URL
            + "/getVilageFcst?serviceKey=a%2Bb%2Fc%3D%3D&pageNo=1&numOfRows=1000&dataType=JSON"
            + "&base_date=20261104&base_time=0500&nx=60&ny=127";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final MovableClock clock =
            new MovableClock(TODAY.atTime(5, 30).atZone(KST).toInstant());

    private KmaShortTermForecast forecast(String serviceKey) {
        return new KmaShortTermForecast(
                builder, null, JsonMapper.builder().build(), clock, KST, serviceKey, BASE_URL, 37.5665, 126.9780);
    }

    private static String twoDays() {
        return KmaSamples.body(
                KmaSamples.item("POP", "20261104", "0700", "30"),
                KmaSamples.item("PTY", "20261104", "1200", "1"),
                KmaSamples.item("POP", "20261104", "1200", "80"),
                KmaSamples.item("POP", "20261104", "2000", "40"),
                KmaSamples.item("POP", "20261105", "0700", "0"),
                KmaSamples.item("POP", "20261105", "2000", "10"));
    }

    @Test
    @DisplayName("위치가 없으면 서울시청 격자(60, 127)로 가장 최근 발표분을 부르고, 같은 발표분은 다시 부르지 않는다")
    void 기본_위치로_부르고_같은_발표분은_캐시한다() {
        server.expect(requestTo(SEOUL_0500))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(twoDays(), MediaType.APPLICATION_JSON));
        KmaShortTermForecast kma = forecast("a+b/c==");

        WeatherOutlook today = kma.outlook(null, null, TODAY);
        clock.advance(Duration.ofHours(2));
        WeatherOutlook tomorrow = kma.outlook(null, null, TODAY.plusDays(1));

        server.verify();
        assertThat(today).isEqualTo(new WeatherOutlook(TODAY, 80, WeatherOutlook.Precipitation.RAIN));
        assertThat(tomorrow).isEqualTo(new WeatherOutlook(TODAY.plusDays(1), 10, null));
    }

    @Test
    @DisplayName("포털의 Encoding 키(% 가 든 값)를 넣어도 한 번 풀어서 같은 주소로 부른다")
    void Encoding_키도_받는다() {
        server.expect(requestTo(SEOUL_0500)).andRespond(withSuccess(twoDays(), MediaType.APPLICATION_JSON));

        assertThat(forecast("a%2Bb%2Fc%3D%3D").outlook(null, null, TODAY)).isNotNull();
        server.verify();
    }

    @Test
    @DisplayName("다음 발표분이 나오면 다시 부른다. 위치를 보내면 그 격자로 부른다(부산 98, 76)")
    void 발표분과_격자가_바뀌면_다시_부른다() {
        server.expect(requestTo(SEOUL_0500)).andRespond(withSuccess(twoDays(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEOUL_0500.replace("base_time=0500", "base_time=0800")))
                .andRespond(withSuccess(twoDays(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        SEOUL_0500.replace("base_time=0500", "base_time=0800").replace("nx=60&ny=127", "nx=98&ny=76")))
                .andRespond(withSuccess(twoDays(), MediaType.APPLICATION_JSON));
        KmaShortTermForecast kma = forecast("a+b/c==");

        kma.outlook(null, null, TODAY);
        clock.advance(Duration.ofHours(3));
        kma.outlook(null, null, TODAY);
        kma.outlook(35.1798, 129.0750, TODAY);

        server.verify();
    }

    @Test
    @DisplayName("부르다 실패하면 null 이고, 그 격자는 5분 동안 다시 부르지 않는다")
    void 실패하면_5분_동안_다시_부르지_않는다() {
        server.expect(requestTo(SEOUL_0500)).andRespond(withServerError());
        server.expect(requestTo(SEOUL_0500))
                .andRespond(withSuccess(
                        "<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR"
                                + "</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>",
                        MediaType.TEXT_XML));
        KmaShortTermForecast kma = forecast("a+b/c==");

        assertThat(kma.outlook(null, null, TODAY)).isNull();
        clock.advance(Duration.ofMinutes(4));
        assertThat(kma.outlook(null, null, TODAY)).isNull();
        clock.advance(Duration.ofMinutes(2));
        assertThat(kma.outlook(null, null, TODAY)).isNull();

        server.verify();
    }

    @Test
    @DisplayName("인증키가 없거나, 지난 날이거나, 사흘 뒤보다 먼 날이면 부르지 않고 null 이다")
    void 부르지_않는_경우() {
        assertThat(forecast("").outlook(null, null, TODAY)).isNull();
        assertThat(forecast("").enabled()).isFalse();
        KmaShortTermForecast kma = forecast("a+b/c==");
        assertThat(kma.outlook(null, null, TODAY.minusDays(1))).isNull();
        assertThat(kma.outlook(null, null, TODAY.plusDays(4))).isNull();

        server.verify();
    }

    @Test
    @DisplayName("응답에 그날이 없으면(예보 범위 끝) null 이다")
    void 응답에_없는_날은_null() {
        server.expect(requestTo(SEOUL_0500)).andRespond(withSuccess(twoDays(), MediaType.APPLICATION_JSON));

        assertThat(forecast("a+b/c==").outlook(null, null, TODAY.plusDays(3))).isNull();
        server.verify();
    }

    /** 시험에서 앞으로 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
