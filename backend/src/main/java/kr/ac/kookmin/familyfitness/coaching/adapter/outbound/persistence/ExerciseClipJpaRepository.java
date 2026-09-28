package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseClipJpaRepository extends JpaRepository<ExerciseClipEntity, String> {
    List<ExerciseClipEntity> findAllByActiveTrueOrderByVideoIdAscStartSecAsc();
}
