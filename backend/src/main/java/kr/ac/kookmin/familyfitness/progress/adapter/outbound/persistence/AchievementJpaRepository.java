package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** 받은 업적 읽기 · 넣기. 지우는 쿼리를 두지 않는다. */
public interface AchievementJpaRepository extends JpaRepository<AchievementEntity, AchievementId> {
    List<AchievementEntity> findByIdProfileId(UUID profileId);

    /**
     * 이미 받은 업적(profile_id, code)이면 아무것도 하지 않고 0 을 돌려준다 — 처음 받은 시각을 그대로 둔다. 먼저 읽고 넣으면 두 보호자가
     * 동시에 붙인 첫 스티커처럼 늦은 쪽이 기본 키 위반으로 통째로 되돌려진다(QA SA-12). 원장 넣기({@link XpEventJpaRepository})와 같은 방식이다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into progress_achievements (profile_id, code, earned_at)
            values (:profileId, :code, :earnedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID profileId, String code, Instant earnedAt);
}
