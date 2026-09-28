package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 칸마다 몇 분을 잡을지 정한다. FE 목 규칙(fe:src/mocks/db.ts {@code sessionsFor} 의 배분 부분)을 그대로 옮겼다(결정 3-1).
 *
 * <ol>
 *   <li>준비 · 정리 칸은 1분씩.
 *   <li>본운동 몫 = max(본운동 칸 수, 요청 분 − 준비 칸 수 − 정리 칸 수).
 *   <li>본운동 칸마다 몫 ÷ 본운동 칸 수(버림), 나머지는 앞 칸부터 1분씩 더한다.
 * </ol>
 *
 * 그래서 칸 분의 합은 보통 요청 분과 같다. 목과 같이 두 경우만 다르다: 칸 수가 요청 분보다 많으면 칸마다 1분이라 합이 더 크고,
 * 본운동 칸이 없으면 준비 · 정리 1분씩만 남는다. AI 는 칸마다 분을 주지 않는다 — 「세트 반복으로 시간을 채우는 것은 화면 몫」
 * (ai:docs/인터페이스-명세.md 4장). 화면은 칸 분 동안 그 영상 구간을 되풀이한다.
 */
public final class SessionMinutesAllocator {
    private SessionMinutesAllocator() {}

    /**
     * @param phases 칸의 단계, 하는 차례대로
     * @param minutes 그 회 운동 시간(요청 분)
     * @return 칸마다 분, {@code phases} 와 같은 차례. 모든 값은 1 이상이다
     */
    public static List<Integer> allocate(List<SessionPhase> phases, int minutes) {
        int warmups = count(phases, SessionPhase.WARMUP);
        int cooldowns = count(phases, SessionPhase.COOLDOWN);
        int mains = count(phases, SessionPhase.MAIN);
        int mainTotal = Math.max(mains, minutes - warmups - cooldowns);
        int base = Math.floorDiv(mainTotal, Math.max(1, mains));
        int extra = mainTotal - base * mains;

        List<Integer> allocated = new ArrayList<>(phases.size());
        int mainIndex = 0;
        for (SessionPhase phase : phases) {
            if (phase == SessionPhase.MAIN) {
                allocated.add(base + (mainIndex < extra ? 1 : 0));
                mainIndex++;
            } else {
                allocated.add(1);
            }
        }
        return List.copyOf(allocated);
    }

    private static int count(List<SessionPhase> phases, SessionPhase phase) {
        return (int) phases.stream().filter(it -> it == phase).count();
    }
}
