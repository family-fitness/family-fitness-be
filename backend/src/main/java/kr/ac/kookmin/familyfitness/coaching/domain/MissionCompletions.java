package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/** 미션 한 건에서 사람마다 끝낸 칸. 끝냈는지는 칸이 아니라 사람마다다(FE 요청서 4장). */
public final class MissionCompletions {
    private static final MissionCompletions NONE = new MissionCompletions(List.of());

    private final Map<UUID, SortedSet<Integer>> positionsByProfile = new HashMap<>();
    private final Map<UUID, SortedSet<LocalDate>> daysByProfile = new HashMap<>();

    private MissionCompletions(Collection<SessionCompletion> completions) {
        for (SessionCompletion it : completions) {
            positionsByProfile
                    .computeIfAbsent(it.profileId(), k -> new TreeSet<>())
                    .add(it.position());
            daysByProfile.computeIfAbsent(it.profileId(), k -> new TreeSet<>()).add(it.completedOn());
        }
    }

    /** 한 미션의 기록만 넘긴다. */
    public static MissionCompletions of(Collection<SessionCompletion> completions) {
        return completions.isEmpty() ? NONE : new MissionCompletions(completions);
    }

    public static MissionCompletions none() {
        return NONE;
    }

    /** 아무도 칸을 끝내지 않았다. */
    public boolean isEmpty() {
        return positionsByProfile.isEmpty();
    }

    /** 이 사람이 끝낸 칸 번호, 오름차순. 없으면 빈 목록. */
    public List<Integer> positionsOf(UUID profileId) {
        SortedSet<Integer> positions = positionsByProfile.get(profileId);
        return positions == null ? List.of() : List.copyOf(positions);
    }

    public boolean has(UUID profileId, int position) {
        SortedSet<Integer> positions = positionsByProfile.get(profileId);
        return positions != null && positions.contains(position);
    }

    /** 이 사람이 칸을 끝낸 날(KST). */
    public Set<LocalDate> daysOf(UUID profileId) {
        SortedSet<LocalDate> days = daysByProfile.get(profileId);
        return days == null ? Set.of() : Set.copyOf(days);
    }
}
