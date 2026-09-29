package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseVideoJpaRepository extends JpaRepository<ExerciseVideoEntity, String> {
    /**
     * 목록에 세울 영상. 공단 영상(mp4 주소가 있는 영상)은 한 편 = 클립 하나라, 새 판에서 빠져 클립이 꺼진 영상은 뺀다.
     * 유튜브 영상은 클립과 상관없이 남긴다(영상 한 편 편성은 클립 없이도 쓴다).
     */
    String LISTED = "(v.mediaUrl is null or exists (select 1 from ExerciseClipEntity c"
            + " where c.videoId = v.videoId and c.active = true))";

    @Query("select v from ExerciseVideoEntity v where v.videoId > :after and " + LISTED + " order by v.videoId")
    List<ExerciseVideoEntity> findListedAfter(@Param("after") String afterVideoId);

    @Query("select v from ExerciseVideoEntity v where " + LISTED + " order by v.videoId")
    List<ExerciseVideoEntity> findListed();

    /** 공단 영상(mp4 주소가 있는 영상)만. 클립에 트는 주소를 붙일 때 쓴다. */
    List<ExerciseVideoEntity> findAllByMediaUrlIsNotNull();

    List<ExerciseVideoEntity> findAllByVideoIdInAndMediaUrlIsNotNull(Collection<String> videoIds);
}
