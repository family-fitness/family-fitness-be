package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRun;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunStatus;
import org.jspecify.annotations.Nullable;

/** 코치 실행 저장소(아웃바운드 포트). 제안 항목은 실행과 함께 저장·복원된다. */
public interface CoachRunRepository {
    CoachRun save(CoachRun run);

    @Nullable
    CoachRun findById(UUID id);

    @Nullable
    CoachRunStatus currentStatus(UUID id);

    boolean existsByFamilyAndStatus(UUID familyId, CoachRunStatus status);

    boolean existsByFamilyAndWeekAndStatusIn(UUID familyId, LocalDate weekStart, Collection<CoachRunStatus> statuses);

    /** 그 주(weekStart) 실행 중 가장 최근 것. */
    @Nullable
    CoachRun findLatestOfWeek(UUID familyId, LocalDate weekStart);

    /**
     * 승인 결과를 조건부 UPDATE(`where status = 'AWAITING_APPROVAL'`) 한 문장으로 반영한다.
     * 동시 승인 경합에서 한쪽만 1행을 바꾼다. 0행이면 false.
     */
    boolean approveIfAwaiting(CoachRun run);

    boolean rejectIfAwaiting(CoachRun run);
}
