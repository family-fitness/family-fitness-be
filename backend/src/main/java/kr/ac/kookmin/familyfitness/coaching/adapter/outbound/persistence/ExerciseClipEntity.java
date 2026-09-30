package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `video_exercises` 행. V132 적재 마이그레이션(scripts/ai_clips_to_sql.py 가 만든다)이 채우고 이 모듈은 읽기만 한다. */
@Entity
@Immutable
@Table(name = "video_exercises")
public class ExerciseClipEntity {
    @Id
    @Column(name = "clip_id", length = 48)
    private String clipId;

    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "name_on_video", nullable = false, length = 60)
    private String nameOnVideo;

    @Column(name = "exercise_name", length = 60)
    private @Nullable String exerciseName;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "fitness_factor", length = 20)
    private @Nullable String fitnessFactor;

    @Column(name = "phase", nullable = false, length = 10)
    private String phase;

    @Column(name = "start_sec", nullable = false)
    private int startSec;

    @Column(name = "end_sec", nullable = false)
    private int endSec;

    @Column(name = "home_ok", nullable = false)
    private boolean homeOk;

    @Column(name = "quiet", nullable = false)
    private boolean quiet;

    @Column(name = "needs_props", nullable = false)
    private boolean needsProps;

    @Column(name = "is_exercise", nullable = false)
    private boolean isExercise;

    @Column(name = "age_group", length = 12)
    private @Nullable String ageGroup;

    @Column(name = "source", length = 10)
    private @Nullable String source;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ExerciseClipEntity() {}

    public ExerciseClip toDomain() {
        return new ExerciseClip(
                clipId,
                videoId,
                seq,
                nameOnVideo,
                exerciseName,
                title,
                fitnessFactor == null ? null : FitnessFactor.valueOf(fitnessFactor),
                SessionPhase.valueOf(phase),
                startSec,
                endSec,
                homeOk,
                quiet,
                needsProps,
                isExercise,
                ageGroup == null ? null : AgeGroup.valueOf(ageGroup),
                source,
                active);
    }
}
