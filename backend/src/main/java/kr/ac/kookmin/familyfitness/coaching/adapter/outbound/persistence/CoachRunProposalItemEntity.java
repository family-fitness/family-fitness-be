package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** `coach_run_proposal_items` 행. participants·citations 는 JSON 텍스트로 보관한다. */
@Entity
@Table(name = "coach_run_proposal_items")
public class CoachRunProposalItemEntity {
    @EmbeddedId
    private ProposalItemId id;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 400)
    private @Nullable String description;

    @Column(name = "rationale", length = 400)
    private @Nullable String rationale;

    @Column(name = "target_metric", nullable = false, length = 20)
    private String targetMetric;

    @Column(name = "target_value", nullable = false)
    private int targetValue;

    @Column(name = "video_id", length = 32)
    private @Nullable String videoId;

    @Column(name = "video_start_sec")
    private @Nullable Integer videoStartSec;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "copy_child", length = 400)
    private @Nullable String copyChild;

    @Column(name = "copy_parent", length = 400)
    private @Nullable String copyParent;

    @Column(name = "participants_json", nullable = false)
    private String participantsJson;

    @Column(name = "citations_json", nullable = false)
    private String citationsJson;

    protected CoachRunProposalItemEntity() {}

    public CoachRunProposalItemEntity(
            ProposalItemId id,
            String title,
            @Nullable String description,
            @Nullable String rationale,
            String targetMetric,
            int targetValue,
            @Nullable String videoId,
            @Nullable Integer videoStartSec,
            LocalDate startsOn,
            LocalDate endsOn,
            @Nullable String copyChild,
            @Nullable String copyParent,
            String participantsJson,
            String citationsJson) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.rationale = rationale;
        this.targetMetric = targetMetric;
        this.targetValue = targetValue;
        this.videoId = videoId;
        this.videoStartSec = videoStartSec;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.copyChild = copyChild;
        this.copyParent = copyParent;
        this.participantsJson = participantsJson;
        this.citationsJson = citationsJson;
    }

    public ProposalItemId getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable String getRationale() {
        return rationale;
    }

    public String getTargetMetric() {
        return targetMetric;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public @Nullable String getVideoId() {
        return videoId;
    }

    public @Nullable Integer getVideoStartSec() {
        return videoStartSec;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public @Nullable String getCopyChild() {
        return copyChild;
    }

    public @Nullable String getCopyParent() {
        return copyParent;
    }

    public String getParticipantsJson() {
        return participantsJson;
    }

    public String getCitationsJson() {
        return citationsJson;
    }
}
