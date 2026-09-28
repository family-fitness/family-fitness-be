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

    /**
     * 미션 행을 SELECT … FOR UPDATE 로 잠그고 읽는다(지우기 · 느낌). 칸 끝 행을 넣는 INSERT 는 외래 키 검사로 미션 행을
     * 잠그므로(PostgreSQL FOR KEY SHARE) 이 잠금과 서로를 기다린다 — 잠근 뒤에 읽은 칸 끝 기록은 커밋까지 그대로다.
     * 없거나, 잠금을 기다리는 사이 다른 트랜잭션이 지우고 커밋했으면 null.
     *
     * <p>같은 트랜잭션에서 이보다 먼저 {@link #findById} 로 읽지 않는다. 먼저 읽은 참여자 엔티티는 영속성 컨텍스트에 남아,
     * 잠근 뒤 다시 읽어도 먼저 읽은 상태가 돌아온다.
     */
    @Nullable
    Mission findByIdForUpdate(UUID id);

    /**
     * 미션 행과 그 참여자 · 칸 행을 지운다. 칸 끝 기록은 지우지 않는다 — 남아 있으면 외래 키에 걸려 실패하니 부르는 쪽이 먼저
     * 막는다({@link Mission#requireCancellableOn}). 느낌(mission_feedback)은 부르는 쪽이 먼저 지운다.
     */
    void delete(UUID id);

    List<Mission> findByFamily(UUID familyId);

    /** {@code from}~{@code to}(양끝 포함)와 기간이 겹치는 가족 미션. */
    List<Mission> findOverlapping(UUID familyId, LocalDate from, LocalDate to);

    int countByCoachRun(UUID coachRunId);

    /** 이 프로필이 참여자이고 기간이 {@code from}~{@code to}(양끝 포함)와 겹치는 미션을, 그 사람의 진행과 함께. */
    List<MissionSpan> spansOf(UUID profileId, LocalDate from, LocalDate to);

    /** {@link #spansOf} 를 여러 프로필에 한 번에 — 누구의 줄인지와 미션을 만든 시각을 같이. 쿼리 한 번이다. */
    List<ParticipantSpan> participantSpansOf(Collection<UUID> profileIds, LocalDate from, LocalDate to);
}
