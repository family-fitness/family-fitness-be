package kr.ac.kookmin.familyfitness.progress.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 한 사람의 이어서 한 날을 셀 창 — {@code today} 부터 거꾸로 {@link Streak#MAX_DAYS} 일 안의 움직인 날 · 쉬는 날 · 잡힌 날.
 *
 * @param moved 서버가 잰 분(타이머 · 영상)이 있는 날
 * @param rest 가족의 쉬는 날
 * @param planned 이 사람에게 운동이 잡힌 날
 */
public record MoveWindow(LocalDate today, Set<LocalDate> moved, Set<LocalDate> rest, Set<LocalDate> planned) {
    public MoveWindow {
        moved = Set.copyOf(moved);
        rest = Set.copyOf(rest);
        planned = Set.copyOf(planned);
    }

    public LocalDate from() {
        return Streak.windowStart(today);
    }

    public int streakDays() {
        return Streak.count(today, moved, rest, planned);
    }

    /** 그날을 움직인 날로 더한 창. 칸을 막 끝낸 날은 활동 기록 순서와 상관없이 움직인 날이다. */
    public MoveWindow withMoved(LocalDate date) {
        Set<LocalDate> next = new HashSet<>(moved);
        next.add(date);
        return new MoveWindow(today, next, rest, planned);
    }

    /** 움직인 날 중 토 · 일이 있는가. */
    public boolean movedOnWeekend() {
        return moved.stream().anyMatch(MoveWindow::isWeekend);
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
