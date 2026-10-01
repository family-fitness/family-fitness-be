package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 탈퇴와 구성원 내보내기의 삭제 쿼리. 가리키는 쪽(자식 행)을 먼저 지우도록 어댑터가 차례를 정한다. id 를 읽는 문장은 JPQL 로
 * 쓴다. H2 는 네이티브 쿼리 로 읽은 uuid 칸을 byte[] 로 돌려줘 UUID 로 바뀌지 않는다.
 */
public interface CoachingErasureJpaRepository extends Repository<MissionEntity, UUID> {
    @Query("""
            select m.id from MissionEntity m
            where m.familyId = :familyId
              and exists (
                  select 1 from MissionParticipantEntity p
                  where p.id.missionId = m.id and p.id.profileId = :profileId)
              and not exists (
                  select 1 from MissionParticipantEntity q
                  where q.id.missionId = m.id and q.id.profileId <> :profileId)
            """)
    List<UUID> findSoloMissions(UUID familyId, UUID profileId);

    @Query("select m.id from MissionEntity m where m.familyId = :familyId")
    List<UUID> findMissionsOfFamily(UUID familyId);

    @Query("select r.id from CoachRunEntity r where r.subjectProfileId = :profileId")
    List<UUID> findRunsAbout(UUID profileId);

    @Query("select r.id from CoachRunEntity r where r.familyId = :familyId")
    List<UUID> findRunsOfFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_feedback where mission_id in (:missionIds)", nativeQuery = true)
    int deleteFeedbackOfMissions(Collection<UUID> missionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_session_completions where mission_id in (:missionIds)", nativeQuery = true)
    int deleteCompletionsOfMissions(Collection<UUID> missionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_participants where mission_id in (:missionIds)", nativeQuery = true)
    int deleteParticipantsOfMissions(Collection<UUID> missionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_sessions where mission_id in (:missionIds)", nativeQuery = true)
    int deleteSessionsOfMissions(Collection<UUID> missionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from missions where id in (:missionIds)", nativeQuery = true)
    int deleteMissions(Collection<UUID> missionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_feedback where profile_id = :profileId", nativeQuery = true)
    int deleteFeedbackOf(UUID profileId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_session_completions where profile_id = :profileId", nativeQuery = true)
    int deleteCompletionsOf(UUID profileId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from mission_participants where profile_id = :profileId", nativeQuery = true)
    int deleteParticipantRowsOf(UUID profileId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update mission_participants set confirmed_by_profile_id = :heir"
                    + " where confirmed_by_profile_id = :profileId",
            nativeQuery = true)
    int handOverConfirmations(UUID profileId, UUID heir);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update missions set created_by = :heir where created_by = :profileId", nativeQuery = true)
    int handOverMissions(UUID profileId, UUID heir);

    /** V1 ck_missions_origin_run: 편성 미션(COACH)만 coach_run_id 가 있다. 연결을 끊으면 직접 만든 미션이 된다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update missions set coach_run_id = null, origin = 'MANUAL' where coach_run_id in (:runIds)",
            nativeQuery = true)
    int detachMissionsFromRuns(Collection<UUID> runIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from coach_run_proposal_sessions where coach_run_id in (:runIds)", nativeQuery = true)
    int deleteProposalSessions(Collection<UUID> runIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from coach_run_proposal_items where coach_run_id in (:runIds)", nativeQuery = true)
    int deleteProposalItems(Collection<UUID> runIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from coach_runs where id in (:runIds)", nativeQuery = true)
    int deleteRuns(Collection<UUID> runIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update coach_runs set requested_by_profile_id = null where requested_by_profile_id = :profileId",
            nativeQuery = true)
    int forgetRequester(UUID profileId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update coach_runs set approved_by = :heir where approved_by = :profileId", nativeQuery = true)
    int handOverApprovals(UUID profileId, UUID heir);

    @Query("""
            select i from CoachRunProposalItemEntity i, CoachRunEntity r
            where r.id = i.id.coachRunId and r.familyId = :familyId
            """)
    List<CoachRunProposalItemEntity> findProposalItemsOfFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CoachRunProposalItemEntity i set i.participantsJson = :participantsJson
            where i.id.coachRunId = :coachRunId and i.id.position = :position
            """)
    int rewriteParticipants(UUID coachRunId, int position, String participantsJson);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from coach_message_citations
            where coach_message_id in (select m.id from coach_messages m where m.profile_id in (:profileIds))
            """, nativeQuery = true)
    int deleteCitations(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from coach_messages where profile_id in (:profileIds)", nativeQuery = true)
    int deleteMessages(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from video_interactions where profile_id in (:profileIds)", nativeQuery = true)
    int deleteVideoInteractions(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from exercise_favorites where profile_id in (:profileIds)", nativeQuery = true)
    int deleteExerciseFavorites(Collection<UUID> profileIds);
}
