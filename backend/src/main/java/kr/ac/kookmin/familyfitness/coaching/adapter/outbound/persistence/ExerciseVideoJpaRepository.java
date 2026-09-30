package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseVideoJpaRepository extends JpaRepository<ExerciseVideoEntity, String> {
    List<ExerciseVideoEntity> findByVideoIdGreaterThanOrderByVideoIdAsc(String afterVideoId);

    List<ExerciseVideoEntity> findAllByOrderByVideoIdAsc();
}
