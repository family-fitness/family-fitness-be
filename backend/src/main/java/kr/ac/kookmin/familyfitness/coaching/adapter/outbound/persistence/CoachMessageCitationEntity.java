package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "coach_message_citations")
public class CoachMessageCitationEntity {
    @EmbeddedId
    private CoachMessageCitationId id;

    @Column(name = "chunk_id", length = 200)
    private @Nullable String chunkId;

    @Column(name = "source_label", nullable = false, length = 200)
    private String sourceLabel;

    @Column(name = "excerpt", length = 500)
    private @Nullable String excerpt;

    @Column(name = "url", length = 500)
    private @Nullable String url;

    protected CoachMessageCitationEntity() {}

    public CoachMessageCitationEntity(
            CoachMessageCitationId id,
            @Nullable String chunkId,
            String sourceLabel,
            @Nullable String excerpt,
            @Nullable String url) {
        this.id = id;
        this.chunkId = chunkId;
        this.sourceLabel = sourceLabel;
        this.excerpt = excerpt;
        this.url = url;
    }

    public CoachMessageCitationId getId() {
        return id;
    }

    public @Nullable String getChunkId() {
        return chunkId;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public @Nullable String getExcerpt() {
        return excerpt;
    }

    public @Nullable String getUrl() {
        return url;
    }
}
