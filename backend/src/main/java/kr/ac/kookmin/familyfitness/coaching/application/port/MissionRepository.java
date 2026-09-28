package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantSpan;
import org.jspecify.annotations.Nullable;

public interface MissionRepository {
    Mission save(Mission mission);

    @Nullable
    Mission findById(UUID id);

    List<Mission> findByFamily(UUID familyId);

    /** {@code from}~{@code to}(양끝 포함)와 기간이 겹치는 가족 미션. */
    List<Mission> findOverlapping(UUID familyId, LocalDate from, LocalDate to);

    int countByCoachRun(UUID coachRunId);

    /** 이 프로필이 참여자이고 기간이 {@code from}~{@code to}(양끝 포함)와 겹치는 미션을, 그 사람의 진행과 함께. */
    List<MissionSpan> spansOf(UUID profileId, LocalDate from, LocalDate to);

    /** {@link #spansOf} 를 여러 프로필에 한 번에 — 누구의 줄인지와 미션을 만든 시각을 같이. 쿼리 한 번이다. */
    List<ParticipantSpan> participantSpansOf(Collection<UUID> profileIds, LocalDate from, LocalDate to);
}
