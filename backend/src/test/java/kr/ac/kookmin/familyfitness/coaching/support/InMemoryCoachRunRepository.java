package kr.ac.kookmin.familyfitness.coaching.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachRunRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunFailureCode;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import org.jspecify.annotations.Nullable;

/**
 * 애플리케이션 테스트용 인메모리 포트 구현. 도메인 객체를 그대로 보관한다(같은 인스턴스).
 * 조건부 UPDATE 는 저장된 상태 스냅샷으로 흉내 내고, (프로필, 날짜) 잠금은 저장된 RUNNING 의 대상 · 날짜로 흉내 낸다.
 */
public class InMemoryCoachRunRepository implements CoachRunRepository {
    public final ConcurrentHashMap<UUID, CoachRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CoachRunStatus> persistedStatus = new ConcurrentHashMap<>();

    @Override
    public CoachRun save(CoachRun run) {
        if (runs.containsKey(run.getId())) {
            throw new IllegalStateException("이미 있는 실행은 조건부 전이로만 바꾼다: run=" + run.getId());
        }
        runs.put(run.getId(), run);
        persistedStatus.put(run.getId(), run.getStatus());
        return run;
    }

    @Override
    public boolean attachAiRunIfRunning(CoachRun run) {
        return persistedStatus.get(run.getId()) == CoachRunStatus.RUNNING;
    }

    @Override
    public boolean finishIfRunning(CoachRun run) {
        if (persistedStatus.get(run.getId()) != CoachRunStatus.RUNNING) return false;
        persistedStatus.put(run.getId(), run.getStatus());
        runs.put(run.getId(), run);
        return true;
    }

    @Override
    public boolean insertRunning(CoachRun run) {
        String lockKey = run.lockKey();
        if (lockKey != null && isLocked(lockKey)) return false;
        save(run);
        return true;
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
    public boolean isLocked(String lockKey) {
        return runs.values().stream().anyMatch(holding(lockKey));
    }

    @Override
    public int failStaleLock(String lockKey, Instant before, String reason, Instant at) {
        return failWhere(holding(lockKey).and(it -> it.getCreatedAt().isBefore(before)), reason, at);
    }

    @Override
    public int failRunningCreatedBefore(Instant before, String reason, Instant at) {
        return failWhere(
                it -> persistedStatus.get(it.getId()) == CoachRunStatus.RUNNING
                        && it.getCreatedAt().isBefore(before),
                reason,
                at);
    }

    @Override
    public int rejectAwaitingOf(UUID subjectProfileId, LocalDate runDate, String reason, Instant at) {
        int rejected = 0;
        for (CoachRun run : runs.values()) {
            if (persistedStatus.get(run.getId()) == CoachRunStatus.AWAITING_APPROVAL
                    && subjectProfileId.equals(run.getSubjectProfileId())
                    && runDate.equals(run.getRunDate())) {
                runs.put(run.getId(), rejected(run, reason, at));
                persistedStatus.put(run.getId(), CoachRunStatus.REJECTED);
                rejected++;
            }
        }
        return rejected;
    }

    @Override
    public @Nullable CoachRun findLatestOfWeek(UUID familyId, LocalDate weekStart) {
        return latest(
                it -> it.getFamilyId().equals(familyId) && it.getWeekStart().equals(weekStart));
    }

    @Override
    public @Nullable CoachRun findLatestOfFamily(UUID familyId) {
        return latest(it -> it.getFamilyId().equals(familyId));
    }

    @Override
    public @Nullable CoachRun findLatestOfSubject(UUID familyId, UUID subjectProfileId) {
        return latest(it -> it.getFamilyId().equals(familyId) && subjectProfileId.equals(it.getSubjectProfileId()));
    }

    @Override
    public boolean approveIfAwaiting(CoachRun run) {
        return transition(run, CoachRunStatus.APPROVED);
    }

    @Override
    public boolean rejectIfAwaiting(CoachRun run) {
        return transition(run, CoachRunStatus.REJECTED);
    }

    private Predicate<CoachRun> holding(String lockKey) {
        return it -> persistedStatus.get(it.getId()) == CoachRunStatus.RUNNING
                && it.getSubjectProfileId() != null
                && it.getRunDate() != null
                && lockKey.equals(CoachRun.lockKeyOf(it.getSubjectProfileId(), it.getRunDate()));
    }

    /** 정리 작업의 조건부 UPDATE 처럼 까닭 코드 STALE 로 끝낸다. */
    private int failWhere(Predicate<CoachRun> condition, String reason, Instant at) {
        int failed = 0;
        for (CoachRun run : runs.values()) {
            if (condition.test(run)) {
                run.fail(CoachRunFailureCode.STALE, reason, at);
                persistedStatus.put(run.getId(), CoachRunStatus.FAILED);
                failed++;
            }
        }
        return failed;
    }

    private @Nullable CoachRun latest(Predicate<CoachRun> condition) {
        return runs.values().stream()
                .filter(condition)
                .max(Comparator.comparing(CoachRun::getCreatedAt))
                .orElse(null);
    }

    private boolean transition(CoachRun run, CoachRunStatus to) {
        if (persistedStatus.get(run.getId()) != CoachRunStatus.AWAITING_APPROVAL) return false;
        persistedStatus.put(run.getId(), to);
        runs.put(run.getId(), run);
        return true;
    }

    /** DB 의 조건부 UPDATE 처럼 승인자 없이 REJECTED 로 바꾼 사본. */
    private static CoachRun rejected(CoachRun r, String reason, Instant at) {
        return CoachRun.reconstitute(
                r.getId(),
                r.getFamilyId(),
                r.getWeekStart(),
                r.getTriggerType(),
                r.getDaysPerWeek(),
                r.getMinutesPerSession(),
                r.getRequestedBy(),
                r.getCreatedAt(),
                r.getSubjectProfileId(),
                r.getRunDate(),
                r.getConditions(),
                CoachRunStatus.REJECTED,
                r.getProposals(),
                r.getSteps(),
                r.getSummary(),
                r.getProposalJson(),
                r.getAiRunId(),
                r.getModelName(),
                r.getApprovedBy(),
                r.getApprovedAt(),
                reason,
                at,
                r.getFailureReason(),
                r.isAiRefused(),
                r.getAiRefusalReason(),
                r.getFailureCode(),
                at);
    }
}
