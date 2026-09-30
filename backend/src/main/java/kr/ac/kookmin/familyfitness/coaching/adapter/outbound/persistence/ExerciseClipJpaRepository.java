package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExerciseClipJpaRepository extends JpaRepository<ExerciseClipEntity, String> {
    List<ExerciseClipEntity> findAllByActiveTrueOrderByVideoIdAscStartSecAsc();

    /**
     * 켜진 클립의 연령대 · 요인 · 단계 줄(V165 video_exercise_labels, 공단 영상만 있다). 한 줄 = {clip_id, age_group, fitness_factor,
     * phase}, 클립 안에서는 AI 표 차례(seq)다. 엔티티로 두지 않는다 — 클립 목록을 펼칠 때만 읽는다.
     */
    @Query(value = """
            select l.clip_id, l.age_group, l.fitness_factor, l.phase
            from video_exercise_labels l join video_exercises c on c.clip_id = l.clip_id
            where c.active = true
            order by l.clip_id, l.seq""", nativeQuery = true)
    List<Object[]> findActiveLabelRows();
}
