package kr.ac.kookmin.familyfitness.league.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * `league_members` — pk(round_id, family_id) · unique(family_id, round_month) · unique(round_id, seat_no).
 * 결과 칸(final_rate · final_score · final_rank · moved)은 정산 UPDATE 로만 채운다.
 *
 * <p>키를 직접 정하는 엔티티라 {@link Persistable} 로 새 행임을 알린다. 그러지 않으면 저장이 merge 로 가서, 같은 (방, 가족) 행을
 * 다른 요청이 먼저 넣었을 때 기본 키 위반 대신 그 행을 읽어 자리 번호를 덮어쓴다(동시 배정 재시도가 돌지 않는다).
 */
@Entity
@Table(name = "league_members")
public class LeagueMemberEntity implements Persistable<LeagueMemberId> {
    @EmbeddedId
    private LeagueMemberId id;

    @Column(name = "round_month", nullable = false)
    private LocalDate roundMonth;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "final_rate")
    private @Nullable Integer finalRate;

    @Column(name = "final_score")
    private @Nullable Double finalScore;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "final_rank")
    private @Nullable Integer finalRank;

    @Column(name = "moved", length = 4)
    private @Nullable String moved;

    @Transient
    private boolean fresh = true;

    protected LeagueMemberEntity() {}

    public LeagueMemberEntity(LeagueMemberId id, LocalDate roundMonth, int seatNo, Instant joinedAt) {
        this.id = id;
        this.roundMonth = roundMonth;
        this.seatNo = seatNo;
        this.joinedAt = joinedAt;
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
    public LeagueMemberId getId() {
        return id;
    }

    public LocalDate getRoundMonth() {
        return roundMonth;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public @Nullable Integer getFinalRate() {
        return finalRate;
    }

    public @Nullable Double getFinalScore() {
        return finalScore;
    }

    public @Nullable Integer getFinalRank() {
        return finalRank;
    }

    public @Nullable String getMoved() {
        return moved;
    }
}
