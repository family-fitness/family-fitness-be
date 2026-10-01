package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 탈퇴와 구성원 내보내기의 삭제 쿼리. 프로필 행을 고치는 문장은 행 버전(version)도 올린다. 그 행을 먼저 읽어 둔 요청이 옛 값으로
 * 덮지 못하고 409 CONFLICT 로 끝나게 하려는 것이다(V145 의 낙관적 잠금).
 */
public interface IdentityErasureJpaRepository extends Repository<FamilyEntity, UUID> {
    @Query(value = "select 1 from families where id = :familyId for update", nativeQuery = true)
    List<Integer> lockFamily(UUID familyId);

    /** id 를 읽는 문장은 JPQL 로 쓴다. H2 는 네이티브 쿼리 로 읽은 uuid 칸을 byte[] 로 돌려줘 UUID 로 바뀌지 않는다. */
    @Query("select c.id from CheerEntity c where c.fromProfileId = :profileId or c.toProfileId = :profileId")
    List<UUID> findCheersOf(UUID profileId);

    @Query("select r.id from CheerEntity r where r.replyToCheerId in :cheerIds")
    List<UUID> findRepliesTo(Collection<UUID> cheerIds);

    @Query("""
            select distinct c.missionId from CheerEntity c
            where c.familyId = :familyId and c.missionId is not null
            """)
    List<UUID> findMissionsOnCheers(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update cheers set mission_id = null where family_id = :familyId and mission_id = :missionId",
            nativeQuery = true)
    int forgetMissionOnCheers(UUID familyId, UUID missionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update cheers set reply_to_cheer_id = null where reply_to_cheer_id in (:cheerIds)",
            nativeQuery = true)
    int unlinkReplies(Collection<UUID> cheerIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from cheers where id in (:cheerIds)", nativeQuery = true)
    int deleteCheers(Collection<UUID> cheerIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update cheers set reply_to_cheer_id = null where family_id = :familyId", nativeQuery = true)
    int unlinkRepliesOfFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from cheers where family_id = :familyId", nativeQuery = true)
    int deleteCheersOfFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from profile_availability_slots where profile_id in (:profileIds)", nativeQuery = true)
    int deleteSlots(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update profile_availability_slots set created_by = :heir where created_by = :profileId",
            nativeQuery = true)
    int handOverSlots(UUID profileId, UUID heir);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from consent_events where profile_id in (:profileIds)", nativeQuery = true)
    int deleteConsentEvents(Collection<UUID> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update consent_events set actor_user_id = null where actor_user_id = :userId", nativeQuery = true)
    int forgetConsentEventActor(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update profiles set consent_by_user_id = null, version = version + 1
            where consent_by_user_id = :userId
            """, nativeQuery = true)
    int forgetConsentGiver(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update profiles set claim_code_issued_by = :heir, version = version + 1
            where claim_code_issued_by = :profileId
            """, nativeQuery = true)
    int handOverInvites(UUID profileId, UUID heir);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update profiles set claim_code_issued_by = null where family_id = :familyId", nativeQuery = true)
    int forgetInvitersOfFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update family_invites set issued_by_profile_id = :heir where issued_by_profile_id = :profileId",
            nativeQuery = true)
    int handOverFamilyInvites(UUID profileId, UUID heir);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update family_invites set consent_by_user_id = null where consent_by_user_id = :userId",
            nativeQuery = true)
    int forgetFamilyInviteConsentGiver(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update family_invites set claimed_by_user_id = null where claimed_by_user_id = :userId",
            nativeQuery = true)
    int forgetFamilyInviteClaimer(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from family_invites where family_id = :familyId", nativeQuery = true)
    int deleteFamilyInvites(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from profiles where id = :profileId", nativeQuery = true)
    int deleteProfile(UUID profileId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from profiles where family_id = :familyId", nativeQuery = true)
    int deleteProfilesOfFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from families where id = :familyId", nativeQuery = true)
    int deleteFamily(UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from refresh_tokens where user_id = :userId", nativeQuery = true)
    int deleteRefreshTokens(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from users where id = :userId", nativeQuery = true)
    int deleteUser(UUID userId);
}
