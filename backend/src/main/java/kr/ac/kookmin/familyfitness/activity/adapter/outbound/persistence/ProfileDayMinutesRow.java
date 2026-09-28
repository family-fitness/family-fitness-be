package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.UUID;

/** JPQL (프로필, 날짜)별 합계 한 줄(초). {@link DayMinutesRow} 를 여러 프로필에 한 번에 읽을 때 쓴다. */
public record ProfileDayMinutesRow(UUID profileId, LocalDate date, Long seconds) {}
