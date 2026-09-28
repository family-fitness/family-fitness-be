package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachRunProposalSessionJpaRepository
        extends JpaRepository<CoachRunProposalSessionEntity, ProposalSessionId> {
    /** 실행 하나의 칸 전부(모든 제안 항목) — 한 번의 조회로 읽는다. */
    List<CoachRunProposalSessionEntity> findByIdCoachRunIdOrderByIdItemPositionAscIdPositionAsc(UUID coachRunId);

    /** 제안 항목을 다시 쓰기 전에 그 칸부터 지운다(칸이 항목을 FK 로 가리킨다). 한 문장. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CoachRunProposalSessionEntity s where s.id.coachRunId = :coachRunId")
    int deleteByCoachRunId(@Param("coachRunId") UUID coachRunId);
}
