package kr.ac.kookmin.familyfitness.coaching.domain;

/** 운동 한 회 안에서 칸이 속한 단계. 준비 · 본 · 정리. */
public enum SessionPhase {
    WARMUP,
    MAIN,
    COOLDOWN;

    /** AI · 영상 라벨의 한글 단계 이름을 읽는다. 모르는 값이나 빈 값은 본운동으로 본다. */
    public static SessionPhase fromKorean(String label) {
        if (label == null) return MAIN;
        return switch (label.strip()) {
            case "준비운동" -> WARMUP;
            case "정리운동" -> COOLDOWN;
            default -> MAIN;
        };
    }
}
