package kr.ac.kookmin.familyfitness.progress.domain;

import java.time.LocalDate;
import java.util.Set;

/**
 * 이어서 한 날(streakDays). 운동이 잡힌 날 기준이다(결정 25).
 *
 * <p>오늘부터 하루씩 거꾸로 {@link #MAX_DAYS} 일까지 본다. 한 날은 이 차례로 가른다.
 *
 * <ol>
 *   <li>움직인 날 → +1. 쉬는 날이어도 움직였으면 센다(FE 규칙 15 「그래도 해서 한 것은 한 만큼이 먼저다」)
 *   <li>오늘 → 건너뛴다. 오늘 아직 안 움직였으면 어제부터 센다
 *   <li>쉬는 날 → 건너뛴다(끊지도 더하지도 않는다, FE 규칙 15)
 *   <li>운동이 잡힌 날 → 끊는다
 *   <li>잡히지 않은 날 → 건너뛴다
 * </ol>
 *
 * FE 목 streakOf(fe:src/mocks/progress.ts:92-100)와 다른 점은 4 · 5 뿐이다. 목은 달력 날마다 끊어서 주 3회 편성을 다 한 아이가
 * 늘 1 이었다. 예: 월 · 목 주 2회를 다 하면 목요일에 2(결정 25).
 */
public final class Streak {
    /** 거꾸로 보는 최대 날 수(목과 같다). */
    public static final int MAX_DAYS = 400;

    private Streak() {}

    /** 셈에 쓰는 가장 이른 날 — 이날부터 {@code today} 까지 {@link #MAX_DAYS} 일. */
    public static LocalDate windowStart(LocalDate today) {
        return today.minusDays(MAX_DAYS - 1L);
    }

    public static int count(LocalDate today, Set<LocalDate> moved, Set<LocalDate> rest, Set<LocalDate> planned) {
        int days = 0;
        for (int back = 0; back < MAX_DAYS; back++) {
            LocalDate date = today.minusDays(back);
            if (moved.contains(date)) {
                days++;
            } else if (back > 0 && !rest.contains(date) && planned.contains(date)) {
                break;
            }
        }
        return days;
    }
}
