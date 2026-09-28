package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;

/** JPQL 날짜별 합계 한 줄. having 으로 0 분인 날은 걸러져 합이 늘 있다(sum 의 결과 타입이 Long 이라 Long 으로 받는다). */
public record DayMinutesRow(LocalDate date, Long minutes) {}
