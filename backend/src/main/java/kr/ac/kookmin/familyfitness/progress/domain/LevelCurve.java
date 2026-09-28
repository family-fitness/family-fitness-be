package kr.ac.kookmin.familyfitness.progress.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 경험치 → 레벨. 레벨 n 이 시작되는 경험치는 FE 목(fe:src/mocks/progress.ts:31)과 FE 요청서 4장 그대로다.
 * 뒤로 갈수록 한 레벨이 길어진다. <b>앞 구간은 바꾸지 않고 뒤에 더하기만 한다</b> — 앞 구간을 올리면 이미 쌓은 사람의 레벨이
 * 내려간다(FE 규칙 10 「레벨은 내려가지 않는다」).
 */
public final class LevelCurve {
    /** 레벨 1 부터 차례로, 그 레벨이 시작되는 경험치. */
    public static final List<Integer> FLOORS = List.of(0, 80, 200, 360, 560, 800, 1080, 1400, 1760, 2160);

    private LevelCurve() {}

    /** 이 경험치의 레벨(1 부터). 구간 시작값과 같으면 그 레벨이다. */
    public static int levelOf(int xp) {
        int level = 1;
        for (int i = 0; i < FLOORS.size(); i++) {
            if (xp >= FLOORS.get(i)) level = i + 1;
        }
        return level;
    }

    /** 이 레벨이 시작된 경험치. */
    public static int floorOf(int level) {
        return FLOORS.get(level - 1);
    }

    /** 다음 레벨이 되는 경험치. 마지막 레벨이면 null. */
    public static @Nullable Integer nextFloorOf(int level) {
        return level < FLOORS.size() ? FLOORS.get(level) : null;
    }
}
