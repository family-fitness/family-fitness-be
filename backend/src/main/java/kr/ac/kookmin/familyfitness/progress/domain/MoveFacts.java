package kr.ac.kookmin.familyfitness.progress.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 칸을 끝낸 순간에 모은, 움직임으로 받는 업적의 조건 값. 조건은 FE 목(fe:src/mocks/progress.ts:178-299)과 같다.
 * 칸을 끝낼 때마다 판정하므로 「처음 닿은 날」 은 「지금 닿아 있는가」 로 가를 수 있다 — 먼저 닿았으면 그때 이미 받았다.
 *
 * @param streakDays 지금 이어서 한 날({@link Streak})
 * @param totalMinutes 지금까지 서버가 잰 분(타이머 · 영상)의 합
 * @param fullSet 오늘 끝낸 칸에 준비 · 본 · 정리가 다 있는가
 * @param weekend 움직인 날 중 토 · 일이 있는가
 * @param together 다른 보호자가 움직인 날과 겹치는 날이 있는가
 */
public record MoveFacts(int streakDays, int totalMinutes, boolean fullSet, boolean weekend, boolean together) {
    /** 조건을 채운 업적. 움직였으니 첫걸음은 늘 들어간다. 이미 받은 것을 빼는 일은 저장하는 쪽이 한다. */
    public Set<Achievement> reached() {
        Set<Achievement> out = EnumSet.of(Achievement.FIRST_STEP);
        if (streakDays >= 3) out.add(Achievement.STREAK_3);
        if (streakDays >= 7) out.add(Achievement.STREAK_7);
        if (fullSet) out.add(Achievement.FULL_SET);
        if (totalMinutes >= 30) out.add(Achievement.MIN_30);
        if (totalMinutes >= 100) out.add(Achievement.MIN_100);
        if (totalMinutes >= 300) out.add(Achievement.MIN_300);
        if (weekend) out.add(Achievement.WEEKEND);
        if (together) out.add(Achievement.TOGETHER);
        return out;
    }
}
