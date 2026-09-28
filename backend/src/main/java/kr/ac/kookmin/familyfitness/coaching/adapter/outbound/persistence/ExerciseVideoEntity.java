package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoLabel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `exercise_videos` 행. AI 영상은 V132 적재 마이그레이션(scripts/ai_clips_to_sql.py)이 채우고 이 모듈은 읽기만 한다. */
@Entity
@Table(name = "exercise_videos")
public class ExerciseVideoEntity {
    @Id
    @Column(name = "video_id", length = 32)
    private String videoId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "channel_name", nullable = false, length = 120)
    private String channelName;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(name = "duration_sec")
    private @Nullable Integer durationSec;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_from")
    private @Nullable Integer ageFrom;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_to")
    private @Nullable Integer ageTo;

    @Column(name = "factors", length = 120)
    private @Nullable String factors;

    @Column(name = "intensity", length = 10)
    private @Nullable String intensity;

    @Column(name = "space", length = 20)
    private @Nullable String space;

    @Column(name = "noise", length = 10)
    private @Nullable String noise;

    @Column(name = "equipment", length = 60)
    private @Nullable String equipment;

    @Column(name = "labeled_by", nullable = false, length = 20)
    private String labeledBy;

    @Column(name = "label_model", length = 40)
    private @Nullable String labelModel;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected ExerciseVideoEntity() {}

    public ExerciseVideoEntity(
            String videoId,
            String title,
            String channelName,
            String channelType,
            @Nullable Integer durationSec,
            @Nullable Integer ageFrom,
            @Nullable Integer ageTo,
            @Nullable String factors,
            @Nullable String intensity,
            @Nullable String space,
            @Nullable String noise,
            @Nullable String equipment,
            String labeledBy,
            @Nullable String labelModel,
            Instant collectedAt) {
        this.videoId = videoId;
        this.title = title;
        this.channelName = channelName;
        this.channelType = channelType;
        this.durationSec = durationSec;
        this.ageFrom = ageFrom;
        this.ageTo = ageTo;
        this.factors = factors;
        this.intensity = intensity;
        this.space = space;
        this.noise = noise;
        this.equipment = equipment;
        this.labeledBy = labeledBy;
        this.labelModel = labelModel;
        this.collectedAt = collectedAt;
    }

    public String getVideoId() {
        return videoId;
    }

    public ExerciseVideo toDomain() {
        return new ExerciseVideo(
                videoId,
                title,
                channelName,
                channelType,
                durationSec,
                new VideoLabel(ageFrom, ageTo, VideoLabel.parseFactors(factors), intensity, space, noise, labelModel),
                equipment,
                labeledBy,
                collectedAt);
    }
}
