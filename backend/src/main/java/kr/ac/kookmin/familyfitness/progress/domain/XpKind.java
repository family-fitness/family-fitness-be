package kr.ac.kookmin.familyfitness.progress.domain;

/**
 * 경험치가 들어온 까닭과 그 양. 값은 FE 목의 적립표(fe:src/mocks/progress.ts:28) 그대로다.
 *
 * <pre>
 * SESSION_DONE  운동 한 칸을 처음 끝냄                 +5
 * MISSION_DONE  운동(미션) 하나를 끝까지 함             +20  (결정 23 — 그날 전부가 아니라 운동마다)
 * STICKER       부모에게서 칭찬 스티커를 받음           +10  (결정 24 — PRAISE 만, 같은 운동에 한 번)
 * REMEASURE     키 · 몸무게를 새로 잼(첫 회차 빼고)     +20  (결정 27)
 * </pre>
 */
public enum XpKind {
    SESSION_DONE(5),
    MISSION_DONE(20),
    STICKER(10),
    REMEASURE(20);

    private final int amount;

    XpKind(int amount) {
        this.amount = amount;
    }

    public int amount() {
        return amount;
    }

    /** 운동으로 받은 것인가(칸 · 미션). 최근 경험치 줄에서 하루치를 한 줄로 묶는다. */
    public boolean isExercise() {
        return this == SESSION_DONE || this == MISSION_DONE;
    }
}
