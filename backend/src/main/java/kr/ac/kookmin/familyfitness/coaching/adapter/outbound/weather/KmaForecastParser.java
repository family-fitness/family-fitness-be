package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import kr.ac.kookmin.familyfitness.coaching.domain.WeatherOutlook;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기상청 단기예보(getVilageFcst) 응답을 날짜별 낮 날씨로 바꾼다.
 * <ul>
 *   <li>발표 시각: 02 · 05 · 08 · 11 · 14 · 17 · 20 · 23시. 발표 뒤 10분쯤 지나야 조회되므로 15분을 기다린 가장 최근 시각을 쓴다.
 *   <li>낮: 예보 시각 07~20시. 그 사이 강수확률(POP)의 최댓값과 처음 나온 강수형태(PTY 1~4)를 고른다.
 *   <li>20시 예보까지 받은 날만 남긴다. 응답 끝에서 잘린 날을 일부 시간만으로 판단하지 않기 위해서다.
 * </ul>
 */
final class KmaForecastParser {
    static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    /** 발표 뒤 조회될 때까지 기다리는 시간(분). */
    static final int PUBLISH_DELAY_MINUTES = 15;

    private static final String DAY_START = "0700";
    private static final String DAY_END = "2000";

    private KmaForecastParser() {}

    /** now(한국 시각)에 조회할 수 있는 가장 최근 발표 시각. */
    static LocalDateTime baseOf(LocalDateTime now) {
        LocalDateTime ready = now.minusMinutes(PUBLISH_DELAY_MINUTES);
        int hour = ready.getHour();
        if (hour < 2) return ready.toLocalDate().minusDays(1).atTime(23, 0);
        return ready.toLocalDate().atTime(hour - (hour - 2) % 3, 0);
    }

    /**
     * 응답 본문을 날짜별 낮 날씨로 바꾼다.
     *
     * @throws IllegalStateException 정상 응답(resultCode 00)이 아니거나 JSON 이 아닐 때. 인증키 오류는 XML 로 오므로 여기에 걸린다.
     */
    static Map<LocalDate, WeatherOutlook> parse(JsonMapper mapper, String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (RuntimeException e) {
            throw new IllegalStateException("기상청 응답이 JSON 이 아니다: " + head(body), e);
        }
        JsonNode response = root.path("response");
        String code = response.path("header").path("resultCode").asString("");
        if (!"00".equals(code)) {
            String message = response.path("header").path("resultMsg").asString("");
            throw new IllegalStateException("기상청 응답 오류 " + code + " " + message + ": " + head(body));
        }

        Map<LocalDate, Day> days = new TreeMap<>();
        for (JsonNode item : response.path("body").path("items").path("item")) {
            String category = item.path("category").asString("");
            if (!category.equals("POP") && !category.equals("PTY")) continue;
            String time = item.path("fcstTime").asString("");
            if (time.compareTo(DAY_START) < 0 || time.compareTo(DAY_END) > 0) continue;
            LocalDate date = LocalDate.parse(item.path("fcstDate").asString(""), DATE);
            Day day = days.computeIfAbsent(date, d -> new Day());
            int value = intOf(item.path("fcstValue").asString(""));
            if (category.equals("POP")) {
                day.maxPop = Math.max(day.maxPop, value);
                if (time.equals(DAY_END)) day.complete = true;
            } else if (value != 0) {
                day.firstPty.putIfAbsent(time, value);
            }
        }

        Map<LocalDate, WeatherOutlook> result = new HashMap<>();
        days.forEach((date, day) -> {
            if (!day.complete || day.maxPop < 0) return;
            WeatherOutlook.Precipitation p = day.firstPty.isEmpty()
                    ? null
                    : WeatherOutlook.Precipitation.ofCode(
                            day.firstPty.firstEntry().getValue());
            result.put(date, new WeatherOutlook(date, Math.min(100, day.maxPop), p));
        });
        return result;
    }

    private static int intOf(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String head(@Nullable String body) {
        if (body == null) return "";
        String flat = body.replaceAll("\\s+", " ");
        return flat.length() <= 200 ? flat : flat.substring(0, 200);
    }

    private static final class Day {
        int maxPop = -1;
        boolean complete;
        final TreeMap<String, Integer> firstPty = new TreeMap<>();
    }
}
