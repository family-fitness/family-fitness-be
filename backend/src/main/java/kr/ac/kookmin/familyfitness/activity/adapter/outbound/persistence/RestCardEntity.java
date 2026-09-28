package kr.ac.kookmin.familyfitness.activity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** `rest_cards` — unique(family_id, rest_date) · unique(family_id, rest_month, card_no). rest_month 는 그달 1일. */
@Entity
@Table(name = "rest_cards")
public class RestCardEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "rest_date", nullable = false)
    private LocalDate restDate;

    @Column(name = "rest_month", nullable = false)
    private LocalDate restMonth;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "card_no", nullable = false)
    private int cardNo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RestCardEntity() {}

    public RestCardEntity(
            UUID id,
            UUID familyId,
            LocalDate restDate,
            LocalDate restMonth,
            int cardNo,
            UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.restDate = restDate;
        this.restMonth = restMonth;
        this.cardNo = cardNo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public LocalDate getRestDate() {
        return restDate;
    }

    public LocalDate getRestMonth() {
        return restMonth;
    }

    public int getCardNo() {
        return cardNo;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
