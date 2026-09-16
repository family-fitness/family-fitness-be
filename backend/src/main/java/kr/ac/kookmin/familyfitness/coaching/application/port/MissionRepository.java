package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import org.jspecify.annotations.Nullable;

public interface MissionRepository {
    Mission save(Mission mission);

    @Nullable
    Mission findById(UUID id);

    List<Mission> findByFamily(UUID familyId);

    /** {@code from}~{@code to}(양끝 포함)와 기간이 겹치는 가족 미션. */
    List<Mission> findOverlapping(UUID familyId, LocalDate from, LocalDate to);

    int countByCoachRun(UUID coachRunId);
}
