package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 한 사람 입장에서 본 미션 하나 — 기간과 그 사람의 진행. 그 운동이 어느 날 「서는지」(그 사람에게 잡힌 날인지)를 가른다.
 * 규칙은 FE 목 standsOn(fe:src/mocks/history.ts:118-132)과 FE 요청서 4장 그대로다.
 *
 * <pre>
 * 걸음수(STEPS) 미션   서지 않는다 — 사람이 말한 값이라 「움직인 날」 이 될 수 없다(fe:src/mocks/league.ts:72 도 뺀다)
 * 하루짜리             그날
 * 여러 날짜리          기간 안의 오늘(하는 중) · 칸을 끝낸 날마다 · 끝내 한 칸도 안 했으면 지난 마지막 날 하루
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

    /** 이 운동이 서는 날. 날짜 차례는 정하지 않는다. */
    public List<LocalDate> standingDays(LocalDate today) {
        if (!targetMetric.isServerVerifiable()) return List.of();
        if (startsOn.equals(endsOn)) return List.of(startsOn);
        List<LocalDate> days = new ArrayList<>();
        if (within(today)) days.add(today);
        doneOn.stream().filter(it -> within(it) && !it.equals(today)).sorted().forEach(days::add);
        if (!started && endsOn.isBefore(today)) days.add(endsOn);
        return days;
    }

    private boolean within(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }
}
