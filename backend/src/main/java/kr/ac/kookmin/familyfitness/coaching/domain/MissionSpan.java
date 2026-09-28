package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 한 사람 입장에서 본 미션 하나 — 기간과 그 사람의 진행. 그 운동이 어느 날 「서는지」(그 사람에게 잡힌 날인지)를 가른다.
 * 규칙은 FE 목 standsOn(fe:src/mocks/history.ts:118-132)과 FE 요청서 4장 그대로다. 캘린더 · 이어서 한 날 · 리그가 이 한 곳으로 센다.
 *
 * <pre>
 * 하루짜리             그날
 * 여러 날짜리          기간 안의 오늘(하는 중) · 칸을 끝낸 날마다 · 끝내 한 칸도 안 했으면 지난 마지막 날 하루
 * 걸음수(STEPS) 미션   캘린더에는 같은 날에 싣지만({@link #calendarDays}) 잡힌 날로는 세지 않는다({@link #standingDays}) —
 *                      사람이 말한 값이라 「움직인 날」 이 될 수 없다(fe:src/mocks/league.ts:72 도 뺀다)
 * </pre>
 *
 * @param started 한 번이라도 진행했는가(끝낸 칸이 있거나, 칸 끝 기록이 없는 옛 미션이면 진행도 &gt; 0 이거나 완료)
 * @param doneOn 이 사람이 칸을 끝낸 날(KST, mission_session_completions.completed_on). 칸 끝 기록이 없는 옛 미션을
 *     완료했으면 완료된 날(verifiedAt 의 KST 날짜) 하나다
 */
public record MissionSpan(
        LocalDate startsOn, LocalDate endsOn, TargetMetric targetMetric, boolean started, Set<LocalDate> doneOn) {
    public MissionSpan {
        doneOn = Set.copyOf(doneOn);
    }

    /**
     * 한 사람의 참여 상태와 칸을 끝낸 날로 만든다 — 저장소(잡힌 날 · 리그)와 캘린더가 같이 쓴다. 칸 끝 기록이 없는 옛 미션을
     * 완료했으면 완료 시각(verifiedAt)의 날짜 하나를 끝낸 날로 본다.
     *
     * @param progressed 진행도가 0 보다 큰가
     * @param doneOn 칸 끝 표의 completed_on
     */
    public static MissionSpan of(
            LocalDate startsOn,
            LocalDate endsOn,
            TargetMetric targetMetric,
            boolean completed,
            boolean progressed,
            @Nullable Instant verifiedAt,
            Set<LocalDate> doneOn,
            ZoneId zone) {
        Set<LocalDate> days = doneOn;
        if (days.isEmpty() && completed && verifiedAt != null) {
            days = Set.of(LocalDate.ofInstant(verifiedAt, zone));
        }
        return new MissionSpan(startsOn, endsOn, targetMetric, !days.isEmpty() || completed || progressed, days);
    }

    /** 이 운동이 잡힌 날(이어서 한 날 · 리그의 분모). 걸음수 미션은 서지 않는다. 날짜 차례는 정하지 않는다. */
    public List<LocalDate> standingDays(LocalDate today) {
        if (!targetMetric.isServerVerifiable()) return List.of();
        return calendarDays(today);
    }

    /**
     * 캘린더에 이 운동이 실리는 날. 날의 규칙은 {@link #standingDays} 와 같고, 걸음수 미션도 싣는다 — 목 dayLogFor 는 지표로
     * 거르지 않고 걸음수 줄의 분만 0 으로 둔다(fe:src/mocks/history.ts:175). 날짜 차례는 정하지 않는다.
     */
    public List<LocalDate> calendarDays(LocalDate today) {
        if (!spansDays()) return List.of(startsOn);
        List<LocalDate> days = new ArrayList<>();
        if (within(today)) days.add(today);
        doneOn.stream().filter(it -> within(it) && !it.equals(today)).sorted().forEach(days::add);
        if (!started && endsOn.isBefore(today)) days.add(endsOn);
        return days;
    }

    /** 여러 날에 걸친 운동인가(startDate &lt; endDate). */
    public boolean spansDays() {
        return !startsOn.equals(endsOn);
    }

    /** 이 사람이 마지막으로 칸을 끝낸 날. 없으면 null. 여러 날짜리의 「다 했어요」 는 이 날 하루에만 선다. */
    public @Nullable LocalDate lastDoneOn() {
        return doneOn.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private boolean within(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }
}
