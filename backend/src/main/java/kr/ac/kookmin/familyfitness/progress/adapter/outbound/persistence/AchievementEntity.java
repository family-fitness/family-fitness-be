package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 받은 업적 한 행. 한 번 쓰고 고치지 않는다(수정자 없음). */
@Entity
@Table(name = "progress_achievements")
public class AchievementEntity {
    @EmbeddedId
    private AchievementId id;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    protected AchievementEntity() {}

    public AchievementEntity(AchievementId id, Instant earnedAt) {
        this.id = id;
        this.earnedAt = earnedAt;
    }

    public AchievementId getId() {
        return id;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }
}
