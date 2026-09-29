package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseVideoJpaRepository extends JpaRepository<ExerciseVideoEntity, String> {
    List<ExerciseVideoEntity> findByVideoIdGreaterThanOrderByVideoIdAsc(String afterVideoId);

    List<ExerciseVideoEntity> findAllByOrderByVideoIdAsc();

    /** 공단 영상(mp4 주소가 있는 영상)만. 클립에 트는 주소를 붙일 때 쓴다. */
    List<ExerciseVideoEntity> findAllByMediaUrlIsNotNull();

    List<ExerciseVideoEntity> findAllByVideoIdInAndMediaUrlIsNotNull(Collection<String> videoIds);
}
