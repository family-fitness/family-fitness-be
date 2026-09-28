package kr.ac.kookmin.familyfitness.shared.ai;

/**
 * 한 회에 클립을 몇 편 담는지 — 준비 · 본 · 정리 가짓수. AI 규칙(ai:src/family_fitness_ai/video/catalog.py {@code SESSION_CLIPS})
 * 그대로다: 10분까지 1 · 3 · 1, 20분까지 2 · 4 · 1, 35분까지 2 · 5 · 2, 그 위 3 · 6 · 3.
 * 영상 길이의 합으로 시간을 채우지 않는다 — 한 편을 여러 세트 되풀이하는 것은 화면 몫이다.
 */
public record SessionClipCounts(int warmup, int main, int cooldown) {
    public static SessionClipCounts of(int minutes) {
        if (minutes <= 10) return new SessionClipCounts(1, 3, 1);
        if (minutes <= 20) return new SessionClipCounts(2, 4, 1);
        if (minutes <= 35) return new SessionClipCounts(2, 5, 2);
        return new SessionClipCounts(3, 6, 3);
    }

    public int total() {
        return warmup + main + cooldown;
    }
}
