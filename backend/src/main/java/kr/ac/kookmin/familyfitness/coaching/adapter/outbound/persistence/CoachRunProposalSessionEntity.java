package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 코치 제안 항목의 칸 한 행. 제안을 붙일 때 한 번 쓰고 고치지 않는다(수정자 없음).
 * 키를 직접 정하는 엔티티라 {@link Persistable} 로 새 행임을 알린다 — 저장 때 행마다 있는지 먼저 읽지(merge) 않는다.
 */
@Entity
@Table(name = "coach_run_proposal_sessions")
public class CoachRunProposalSessionEntity implements Persistable<ProposalSessionId> {
    @EmbeddedId
    private ProposalSessionId id;

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

    @Transient
    private boolean fresh = true;

    protected CoachRunProposalSessionEntity() {}

    public CoachRunProposalSessionEntity(
            ProposalSessionId id,
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

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @Override
    public ProposalSessionId getId() {
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
