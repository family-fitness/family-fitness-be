package kr.ac.kookmin.familyfitness.identity.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 운동할 수 있는 시간 한 칸 — 요일 · 시작 시각(한국 시각, 초 없음) · 분.
 * 값 규칙(분 5~120 · 하루 한 칸)은 identity 가 저장할 때 이미 지켰다. 받는 쪽은 다시 검사하지 않는다.
 */
public record AvailabilitySlot(DayOfWeek day, LocalTime start, int minutes) {}
