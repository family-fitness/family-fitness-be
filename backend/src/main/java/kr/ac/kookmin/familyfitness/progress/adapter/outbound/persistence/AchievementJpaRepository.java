package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 받은 업적 읽기 · 넣기. 지우는 쿼리를 두지 않는다. */
public interface AchievementJpaRepository extends JpaRepository<AchievementEntity, AchievementId> {
    List<AchievementEntity> findByIdProfileId(UUID profileId);
}
