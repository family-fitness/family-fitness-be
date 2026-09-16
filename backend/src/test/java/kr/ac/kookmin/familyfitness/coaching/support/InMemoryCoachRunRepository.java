package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import org.jspecify.annotations.Nullable;

/**
 * 애플리케이션 테스트용 인메모리 포트 구현. 도메인 객체를 그대로 보관한다(같은 인스턴스).
 * 조건부 UPDATE 는 저장된 상태 스냅샷으로 흉내 낸다.
 */
public class InMemoryCoachRunRepository implements CoachRunRepository {
    public final ConcurrentHashMap<UUID, CoachRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CoachRunStatus> persistedStatus = new ConcurrentHashMap<>();

    @Override
    public CoachRun save(CoachRun run) {
        runs.put(run.getId(), run);
        persistedStatus.put(run.getId(), run.getStatus());
        return run;
    }

    @Override
    public @Nullable CoachRun findById(UUID id) {
        return runs.get(id);
    }

    @Override
    public @Nullable CoachRunStatus currentStatus(UUID id) {
        return persistedStatus.get(id);
    }

    @Override
    public boolean existsByFamilyAndStatus(UUID familyId, CoachRunStatus status) {
        return runs.values().stream()
                .anyMatch(it -> it.getFamilyId().equals(familyId) && persistedStatus.get(it.getId()) == status);
    }

    @Override
    public boolean existsByFamilyAndWeekAndStatusIn(
            UUID familyId, LocalDate weekStart, Collection<CoachRunStatus> statuses) {
        return runs.values().stream()
                .anyMatch(it -> it.getFamilyId().equals(familyId)
                        && it.getWeekStart().equals(weekStart)
                        && statuses.contains(persistedStatus.get(it.getId())));
    }

    @Override
    public @Nullable CoachRun findLatestOfWeek(UUID familyId, LocalDate weekStart) {
        return runs.values().stream()
                .filter(it ->
                        it.getFamilyId().equals(familyId) && it.getWeekStart().equals(weekStart))
                .max(Comparator.comparing(CoachRun::getCreatedAt))
                .orElse(null);
    }

    @Override
    public boolean approveIfAwaiting(CoachRun run) {
        return transition(run, CoachRunStatus.APPROVED);
    }

    @Override
    public boolean rejectIfAwaiting(CoachRun run) {
        return transition(run, CoachRunStatus.REJECTED);
    }

    private boolean transition(CoachRun run, CoachRunStatus to) {
        if (persistedStatus.get(run.getId()) != CoachRunStatus.AWAITING_APPROVAL) return false;
        persistedStatus.put(run.getId(), to);
        runs.put(run.getId(), run);
        return true;
    }
}
