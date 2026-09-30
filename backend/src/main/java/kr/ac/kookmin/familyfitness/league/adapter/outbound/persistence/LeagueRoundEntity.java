package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * `league_rounds` — unique(round_month, tier, group_no). round_month 는 그달 1일. settled_at 은 조건부 UPDATE 로만 채운다.
 * 키를 직접 정하는 엔티티라 {@link Persistable} 로 새 행임을 알린다 — 저장이 먼저 읽고(merge) 덮어쓰지 않고 곧바로 INSERT 한다.
 */
@Entity
@Table(name = "league_rounds")
public class LeagueRoundEntity implements Persistable<UUID> {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "round_month", nullable = false)
    private LocalDate roundMonth;

    @Column(name = "tier", nullable = false, length = 10)
    private String tier;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "group_no", nullable = false)
    private int groupNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private @Nullable Instant settledAt;

    @Transient
    private boolean fresh = true;

    protected LeagueRoundEntity() {}

    public LeagueRoundEntity(UUID id, LocalDate roundMonth, String tier, int groupNo, Instant createdAt) {
        this.id = id;
        this.roundMonth = roundMonth;
        this.tier = tier;
        this.groupNo = groupNo;
        this.createdAt = createdAt;
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
    public UUID getId() {
        return id;
    }

    public LocalDate getRoundMonth() {
        return roundMonth;
    }

    public String getTier() {
        return tier;
    }

    public int getGroupNo() {
        return groupNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getSettledAt() {
        return settledAt;
    }
}
