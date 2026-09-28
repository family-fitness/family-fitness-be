package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 한 사람 입장에서 본 미션 하나 — 기간과 그 사람의 진행. 그 운동이 어느 날 「서는지」(그 사람에게 잡힌 날인지)를 가른다.
 * 규칙은 FE 목 standsOn(fe:src/mocks/history.ts:118-132)과 FE 요청서 4장 그대로다.
 *
 * <pre>
 * 걸음수(STEPS) 미션   서지 않는다 — 사람이 말한 값이라 「움직인 날」 이 될 수 없다(fe:src/mocks/league.ts:72 도 뺀다)
 * 하루짜리             그날
 * 여러 날짜리          기간 안의 오늘(하는 중) · 끝낸 날 · 끝내 한 번도 안 했으면 지난 마지막 날 하루
 * </pre>
 *
 * 여러 날짜리의 「끝낸 날」 은 목에서 칸을 끝낸 날마다다. 칸마다 끝낸 날은 아직 저장하지 않으므로 지금은 참여자가 완료된 날
 * (verifiedAt 의 KST 날짜) 하나로 본다. 칸을 끝낸 날은 곧 움직인 날이라 이어서 한 날 셈에서는 결과가 같다.
 *
 * @param started 한 번이라도 진행했는가(진행도 &gt; 0 이거나 완료)
 * @param completedAt 이 사람이 완료된 시각. 아직이면 null
 */
public record MissionSpan(
        LocalDate startsOn,
        LocalDate endsOn,
        TargetMetric targetMetric,
        boolean started,
        @Nullable Instant completedAt) {
    /** 이 운동이 서는 날. 날짜 차례는 정하지 않는다. */
    public List<LocalDate> standingDays(LocalDate today, ZoneId zone) {
        if (!targetMetric.isServerVerifiable()) return List.of();
        if (startsOn.equals(endsOn)) return List.of(startsOn);
        List<LocalDate> days = new ArrayList<>();
        if (within(today)) days.add(today);
        if (completedAt != null) {
            LocalDate completedOn = LocalDate.ofInstant(completedAt, zone);
            if (within(completedOn) && !completedOn.equals(today)) days.add(completedOn);
        }
        if (!started && endsOn.isBefore(today)) days.add(endsOn);
        return days;
    }

    private boolean within(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }
}
