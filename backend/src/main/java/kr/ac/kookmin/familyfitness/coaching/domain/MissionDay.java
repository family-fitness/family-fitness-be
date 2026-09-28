package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 한 사람 · 하루에서 본 미션 하나 — 캘린더 entries[] 한 줄의 셈. 규칙은 FE 목 entryOf · plannedOf
 * (fe:src/mocks/history.ts:145-188) 그대로다. 어느 날에 싣는지는 {@link MissionSpan#calendarDays} 가 정한다.
 *
 * <pre>
 * 칸 done       하루짜리를 다 했으면 칸 전부. 아니면 이 사람이 끝낸 칸 — 여러 날짜리는 그날 끝낸 칸만
 * completed     다 했는가. 여러 날짜리는 마지막 칸을 끝낸 날 하루에만 true
 * minutes       done 인 칸의 잡힌 분 합. 칸이 없으면 진행도 × 잡힌 분(반올림). 걸음수는 0
 * plannedMinutes 칸 분 합. 칸이 없으면 분 목표(TIMER_MINUTES)의 목표값, 둘 다 아니면 0
 * </pre>
 *
 * @param mine 이 사람이 끝낸 칸(position → 칸 끝 기록). 형제 · 보호자의 기록은 넣지 않는다
 */
public record MissionDay(
        Mission mission, MissionParticipant me, Map<Integer, SessionCompletion> mine, MissionSpan span, LocalDate day) {
    public MissionDay {
        mine = Map.copyOf(mine);
    }

    /** 그날 잡혀 있던 분. */
    public int plannedMinutes() {
        if (mission.hasSessions()) return MissionSession.totalMinutes(mission.getSessions());
        return mission.getTargetMetric() == TargetMetric.TIMER_MINUTES ? mission.getTargetValue() : 0;
    }

    /** 이 칸을 이 사람이 그날 끝냈는가. 끝낸 날을 모르는 기록은 없다 — 칸 끝 표는 completed_on 을 늘 든다. */
    public boolean isDone(MissionSession session) {
        if (me.isCompleted() && !span.spansDays()) return true;
        SessionCompletion row = mine.get(session.position());
        return row != null && (!span.spansDays() || row.completedOn().equals(day));
    }

    /** 그날 끝낸 칸의 확인 방법. 칸 끝 기록이 있으면 그 값, 기록 없이 완료로 끝난 칸은 참여자의 값, 안 끝냈으면 null. */
    public @Nullable VerifiedBy verifiedByOf(MissionSession session) {
        if (!isDone(session)) return null;
        SessionCompletion row = mine.get(session.position());
        return row != null ? row.verifiedBy() : me.getVerifiedBy();
    }

    /** 다 했는가. 여러 날짜리는 마지막 칸을 끝낸 날 하루에만 — 날마다 「다 했어요」 로 세지 않는다. */
    public boolean completed() {
        if (!me.isCompleted() || !span.spansDays()) return me.isCompleted();
        LocalDate last = span.lastDoneOn();
        return last == null || last.equals(day);
    }

    /** 그날 한 분. 칸이 있으면 끝낸 칸의 잡힌 분 합, 없으면 진행도로 셈한다. 걸음수는 분이 아니라 0 이다. */
    public int minutes() {
        if (mission.getTargetMetric() == TargetMetric.STEPS) return 0;
        if (!mission.hasSessions()) return (int) Math.round(me.getProgress() * plannedMinutes());
        return mission.getSessions().stream()
                .filter(this::isDone)
                .mapToInt(MissionSession::minutes)
                .sum();
    }
}
