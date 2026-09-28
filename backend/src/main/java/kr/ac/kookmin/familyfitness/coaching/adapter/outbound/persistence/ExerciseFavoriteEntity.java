package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** `exercise_favorites` 행(V141). 쓰기는 {@link ExerciseFavoriteJpaRepository} 의 INSERT · DELETE 문으로만 한다. */
@Entity
@Table(name = "exercise_favorites")
public class ExerciseFavoriteEntity {
    @EmbeddedId
    private ExerciseFavoriteId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExerciseFavoriteEntity() {}

    public ExerciseFavoriteId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
