package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Embeddable
public class FitnessTestItemEmbeddable {
    @Column(name = "item_code", nullable = false, length = 3)
    private String itemCode;

    @Column(name = "raw_value", nullable = false, precision = 8, scale = 3)
    private BigDecimal rawValue;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "percentile")
    private @Nullable Integer percentile;

    @Column(name = "grade", length = 10)
    private @Nullable String grade;

    @Column(name = "band", length = 10)
    private @Nullable String band;

    protected FitnessTestItemEmbeddable() {}

    public FitnessTestItemEmbeddable(
            String itemCode,
            BigDecimal rawValue,
            @Nullable Integer percentile,
            @Nullable String grade,
            @Nullable String band) {
        this.itemCode = itemCode;
        this.rawValue = rawValue;
        this.percentile = percentile;
        this.grade = grade;
        this.band = band;
    }

    public String getItemCode() {
        return itemCode;
    }

    public BigDecimal getRawValue() {
        return rawValue;
    }

    public @Nullable Integer getPercentile() {
        return percentile;
    }

    public @Nullable String getGrade() {
        return grade;
    }

    public @Nullable String getBand() {
        return band;
    }
}
