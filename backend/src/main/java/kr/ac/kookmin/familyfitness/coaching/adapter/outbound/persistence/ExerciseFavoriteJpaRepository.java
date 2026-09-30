package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExerciseFavoriteJpaRepository extends JpaRepository<ExerciseFavoriteEntity, ExerciseFavoriteId> {
    @Query("select f.id.clipId from ExerciseFavoriteEntity f where f.id.profileId = :profileId")
    List<String> findClipIdsByProfileId(UUID profileId);

    /**
     * 이미 있는 (프로필, 클립) 이면 아무것도 하지 않는다. 먼저 읽고 넣으면 같은 요청 둘이 함께 「없음」 을 보고 하나가 PK 충돌(409)로
     * 끝나서, 충돌을 DB 가 삼키게 한다. ON CONFLICT DO NOTHING 은 PostgreSQL 과 H2(MODE=PostgreSQL) 둘 다 받는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into exercise_favorites (profile_id, clip_id, created_at)
            values (:profileId, :clipId, :createdAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID profileId, String clipId, Instant createdAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExerciseFavoriteEntity f where f.id.profileId = :profileId and f.id.clipId = :clipId")
    int deleteOne(UUID profileId, String clipId);
}
