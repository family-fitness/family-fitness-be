package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운동 느낌 한 줄(V147). 읽기용 — 넣기 · 덮어쓰기는 {@link MissionFeedbackJpaRepository} 의 쿼리로 한다. */
@Entity
@Table(name = "mission_feedback")
public class MissionFeedbackEntity {
    @EmbeddedId
    private MissionFeedbackId id;

    @Column(name = "feel", nullable = false, length = 10)
    private String feel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MissionFeedbackEntity() {}

    public MissionFeedbackId getId() {
        return id;
    }

    public String getFeel() {
        return feel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
