package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;

/**
 * 하루에 서버가 잰 활동(TIMER + VIDEO). 초가 0 인 날은 만들지 않는다.
 *
 * @param minutes 그날 초 합을 60 으로 나눠 내림한 분. 1분이 안 되게 움직인 날은 0 이다(그래도 움직인 날이다)
 */
public record DailyMinutes(LocalDate date, int minutes) {}
