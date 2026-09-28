package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;

/** 하루에 서버가 잰 분(TIMER + VIDEO). 0 분인 날은 만들지 않는다. */
public record DailyMinutes(LocalDate date, int minutes) {}
