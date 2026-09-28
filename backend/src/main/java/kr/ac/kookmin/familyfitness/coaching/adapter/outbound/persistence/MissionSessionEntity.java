package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** 미션의 칸 한 행. 미션을 만들 때 한 번 쓰고 고치지 않는다(수정자 없음). */
@Entity
@Table(name = "mission_sessions")
public class MissionSessionEntity {
    @EmbeddedId
    private MissionSessionId id;

    @Column(name = "phase", nullable = false, length = 10)
    private String phase;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    /** 한글 요인 이름(예: 유연성) */
    @Column(name = "factor", length = 20)
    private @Nullable String factor;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "video_id", length = 32)
    private @Nullable String videoId;

    @Column(name = "start_sec")
    private @Nullable Integer startSec;

    @Column(name = "end_sec")
    private @Nullable Integer endSec;

    @Column(name = "clip_title", length = 120)
    private @Nullable String clipTitle;

    protected MissionSessionEntity() {}

    public MissionSessionEntity(
            MissionSessionId id,
            String phase,
            String title,
            @Nullable String factor,
            int minutes,
            @Nullable String videoId,
            @Nullable Integer startSec,
            @Nullable Integer endSec,
            @Nullable String clipTitle) {
        this.id = id;
        this.phase = phase;
        this.title = title;
        this.factor = factor;
        this.minutes = minutes;
        this.videoId = videoId;
        this.startSec = startSec;
        this.endSec = endSec;
        this.clipTitle = clipTitle;
    }

    public MissionSessionId getId() {
        return id;
    }

    public String getPhase() {
        return phase;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getFactor() {
        return factor;
    }

    public int getMinutes() {
        return minutes;
    }

    public @Nullable String getVideoId() {
        return videoId;
    }

    public @Nullable Integer getStartSec() {
        return startSec;
    }

    public @Nullable Integer getEndSec() {
        return endSec;
    }

    public @Nullable String getClipTitle() {
        return clipTitle;
    }
}
