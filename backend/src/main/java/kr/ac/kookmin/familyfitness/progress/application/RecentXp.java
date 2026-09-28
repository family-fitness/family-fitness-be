package kr.ac.kookmin.familyfitness.progress.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;
import kr.ac.kookmin.familyfitness.progress.domain.XpKind;

/**
 * 최근 경험치 줄. FE 목(fe:src/mocks/progress.ts:115-131, :168)처럼 운동(칸 · 미션)은 하루를 한 줄로 묶고, 스티커 · 다시 재기는
 * 한 건마다 한 줄이다. 줄 차례는 그 줄의 원장 행을 가장 늦게 적은 시각이다(최근 것부터). 다섯 줄까지.
 */
final class RecentXp {
    static final int LINES = 5;

    private RecentXp() {}

    /**
     * @param recent 원장 최근 행(최근에 적은 것부터)
     * @param exerciseRowsOn 그 날들에 운동으로 받은 행 전부 — 하루 줄의 합을 {@code recent} 가 잘라 먹지 않게 따로 읽는다
     */
    static List<XpLineView> linesOf(List<XpEvent> recent, Function<Set<LocalDate>, List<XpEvent>> exerciseRowsOn) {
        List<XpEvent> heads = new ArrayList<>();
        Set<LocalDate> exerciseDays = new LinkedHashSet<>();
        for (XpEvent row : recent) {
            if (heads.size() == LINES) break;
            if (!row.kind().isExercise() || exerciseDays.add(row.occurredOn())) heads.add(row);
        }
        Map<LocalDate, List<XpEvent>> byDay = exerciseDays.isEmpty()
                ? Map.of()
                : exerciseRowsOn.apply(exerciseDays).stream().collect(Collectors.groupingBy(XpEvent::occurredOn));
        return heads.stream()
                .map(head -> head.kind().isExercise()
                        ? exerciseLine(head.occurredOn(), byDay.getOrDefault(head.occurredOn(), List.of(head)))
                        : new XpLineView(head.kind(), head.fromProfileId(), head.amount(), head.occurredOn()))
                .toList();
    }

    /** 하루 운동 한 줄 — 그날 끝까지 한 운동이 있으면 MISSION_DONE, 없으면 SESSION_DONE. 양은 그날 칸 · 미션의 합. */
    private static XpLineView exerciseLine(LocalDate day, List<XpEvent> rows) {
        boolean missionDone = rows.stream().anyMatch(it -> it.kind() == XpKind.MISSION_DONE);
        int amount = rows.stream().mapToInt(XpEvent::amount).sum();
        return new XpLineView(missionDone ? XpKind.MISSION_DONE : XpKind.SESSION_DONE, null, amount, day);
    }
}
