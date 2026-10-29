package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.weather;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.coaching.application.port.WeatherForecast;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherOutlook;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기상청 단기예보 조회서비스(공공데이터포털 15084084)의 getVilageFcst 로 하루 낮 날씨를 읽는다.
 * <ul>
 *   <li>인증키(app.weather.kma.service-key)가 비어 있으면 부르지 않고 null 이다. 편성은 날씨 없이 그대로 간다.
 *   <li>격자 하나와 발표 시각 하나에 한 번만 부른다. 결과는 다음 발표 시각까지(3시간) 메모리에 둔다.
 *   <li>부르다 실패하면 그 격자는 5분 동안 다시 부르지 않는다. 기상청이 느릴 때 편성 요청마다 기다리지 않게 하기 위해서다.
 *   <li>예보가 닿는 날(오늘부터 사흘 뒤까지)이 아니면 부르지 않는다.
 * </ul>
 * 인증키는 포털의 「Decoding」 키를 넣는다. 「Encoding」 키(% 가 들어간 값)를 넣어도 한 번 풀어서 쓴다.
 */
@Component
public class KmaShortTermForecast implements WeatherForecast {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    static final Duration FAILURE_BACKOFF = Duration.ofMinutes(5);
    static final int MAX_DAYS_AHEAD = 3;
    static final int NUM_OF_ROWS = 1000;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RestClient client;
    private final JsonMapper mapper;
    private final String serviceKey;
    private final double defaultLatitude;
    private final double defaultLongitude;
    private final Clock clock;
    private final ZoneId zone;

    private final Map<CacheKey, Map<LocalDate, WeatherOutlook>> cache = new ConcurrentHashMap<>();
    private final Map<KmaGrid, Instant> failedUntil = new ConcurrentHashMap<>();

    @Autowired
    public KmaShortTermForecast(
            RestClient.Builder builder,
            JsonMapper mapper,
            Clock clock,
            ZoneId zone,
            @Value("${app.weather.kma.service-key:}") String serviceKey,
            @Value("${app.weather.kma.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}")
                    String baseUrl,
            @Value("${app.weather.default-latitude:37.5665}") double defaultLatitude,
            @Value("${app.weather.default-longitude:126.9780}") double defaultLongitude) {
        this(builder, jdkFactory(), mapper, clock, zone, serviceKey, baseUrl, defaultLatitude, defaultLongitude);
    }

    /** @param factory 요청 팩토리. null 이면 빌더의 것을 그대로 쓴다(테스트의 MockRestServiceServer). */
    KmaShortTermForecast(
            RestClient.Builder builder,
            @Nullable ClientHttpRequestFactory factory,
            JsonMapper mapper,
            Clock clock,
            ZoneId zone,
            String serviceKey,
            String baseUrl,
            double defaultLatitude,
            double defaultLongitude) {
        RestClient.Builder b = builder.clone().baseUrl(baseUrl);
        if (factory != null) b.requestFactory(factory);
        this.client = b.build();
        this.mapper = mapper;
        this.clock = clock;
        this.zone = zone;
        this.serviceKey = decodeIfEncoded(serviceKey.trim());
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
    }

    public boolean enabled() {
        return !serviceKey.isEmpty();
    }

    @Override
    public @Nullable WeatherOutlook outlook(@Nullable Double latitude, @Nullable Double longitude, LocalDate date) {
        if (!enabled()) return null;
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);
        LocalDate today = now.toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) return null;

        KmaGrid grid = KmaGrid.of(
                latitude != null ? latitude : defaultLatitude, longitude != null ? longitude : defaultLongitude);
        CacheKey key = new CacheKey(grid, KmaForecastParser.baseOf(now));
        Map<LocalDate, WeatherOutlook> days = cache.get(key);
        if (days == null) {
            Instant blockedUntil = failedUntil.get(grid);
            if (blockedUntil != null && clock.instant().isBefore(blockedUntil)) return null;
            days = fetch(key);
            if (days == null) {
                failedUntil.put(grid, clock.instant().plus(FAILURE_BACKOFF));
                return null;
            }
            failedUntil.remove(grid);
            cache.keySet().removeIf(k -> k.grid().equals(grid) && !k.base().equals(key.base()));
            cache.put(key, days);
        }
        return days.get(date);
    }

    private @Nullable Map<LocalDate, WeatherOutlook> fetch(CacheKey key) {
        try {
            String body = client.get()
                    .uri(
                            "/getVilageFcst?serviceKey={key}&pageNo=1&numOfRows={rows}&dataType=JSON"
                                    + "&base_date={date}&base_time={time}&nx={nx}&ny={ny}",
                            serviceKey,
                            NUM_OF_ROWS,
                            key.base().format(KmaForecastParser.DATE),
                            key.base().format(KmaForecastParser.TIME),
                            key.grid().nx(),
                            key.grid().ny())
                    .retrieve()
                    .body(String.class);
            if (body == null) throw new IllegalStateException("기상청 응답이 비어 있다");
            return KmaForecastParser.parse(mapper, body);
        } catch (RuntimeException e) {
            log.warn(
                    "기상청 단기예보를 읽지 못했다(격자 {},{} · 발표 {}). {}분 동안 날씨 없이 짠다: {}",
                    key.grid().nx(),
                    key.grid().ny(),
                    key.base(),
                    FAILURE_BACKOFF.toMinutes(),
                    e.toString());
            return null;
        }
    }

    private static String decodeIfEncoded(String key) {
        return key.contains("%") ? URLDecoder.decode(key, StandardCharsets.UTF_8) : key;
    }

    /** JDK HttpClient. 연결 1초 · 읽기 3초. 일부 공공 API 게이트웨이가 HTTP/2 를 잘 받지 않아 HTTP/1.1 로 고정한다. */
    private static ClientHttpRequestFactory jdkFactory() {
        return ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(c -> c.version(HttpClient.Version.HTTP_1_1))
                .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT));
    }

    private record CacheKey(KmaGrid grid, LocalDateTime base) {}
}
