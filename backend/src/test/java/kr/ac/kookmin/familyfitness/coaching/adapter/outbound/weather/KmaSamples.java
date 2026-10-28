package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.weather;

import java.util.Arrays;
import java.util.stream.Collectors;

/** 기상청 단기예보(getVilageFcst) 응답 모양을 흉내 낸 본문. 항목 칸은 실제 응답과 같다. */
final class KmaSamples {
    private KmaSamples() {}

    static String item(String category, String fcstDate, String fcstTime, String fcstValue) {
        return "{\"baseDate\":\"20261104\",\"baseTime\":\"0500\",\"category\":\"" + category + "\",\"fcstDate\":\""
                + fcstDate + "\",\"fcstTime\":\"" + fcstTime + "\",\"fcstValue\":\"" + fcstValue
                + "\",\"nx\":60,\"ny\":127}";
    }

    static String body(String... items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_SERVICE\"},"
                + "\"body\":{\"dataType\":\"JSON\",\"items\":{\"item\":["
                + Arrays.stream(items).collect(Collectors.joining(","))
                + "]},\"pageNo\":1,\"numOfRows\":1000,\"totalCount\":" + items.length + "}}}";
    }
}
