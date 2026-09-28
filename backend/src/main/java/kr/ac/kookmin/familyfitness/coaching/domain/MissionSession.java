package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.Comparator;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 미션 한 건 안의 칸 하나. {@code position} 이 곧 하는 차례다(1부터).
 * 끝냈는지는 칸이 아니라 사람마다 든다 — 칸에는 완료 값을 두지 않는다.
 */
public record MissionSession(
        int position,
        SessionPhase phase,
        String title,
        @Nullable FitnessFactor factor,
        int minutes,
        @Nullable SessionClip clip) {
    public MissionSession {
        if (position < 1) throw new IllegalArgumentException("칸 번호(position)는 1부터다");
        if (title.isBlank()) throw new IllegalArgumentException("칸 제목이 비었다");
        if (minutes < 1) throw new IllegalArgumentException("칸 시간(minutes)은 1분 이상이어야 한다");
    }

    /** position 차례로 세운다. 번호가 1..n 으로 빈틈없이 이어지지 않으면(겹침 · 빠짐) 거부한다. */
    public static List<MissionSession> ordered(List<MissionSession> sessions) {
        List<MissionSession> sorted = sessions.stream()
                .sorted(Comparator.comparingInt(MissionSession::position))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).position() != i + 1) {
                throw new IllegalArgumentException("칸 번호(position)는 1부터 " + sorted.size() + "까지 겹치지 않아야 한다");
            }
        }
        return sorted;
    }

    public static int totalMinutes(List<MissionSession> sessions) {
        return sessions.stream().mapToInt(MissionSession::minutes).sum();
    }
}
