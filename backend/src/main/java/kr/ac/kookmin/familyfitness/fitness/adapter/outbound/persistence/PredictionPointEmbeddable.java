package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Embeddable
public class PredictionPointEmbeddable {
    @Column(name = "scenario", nullable = false, length = 20)
    private String scenario;

    @Column(name = "item_code", nullable = false, length = 3)
    private String itemCode;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "years_from_now", nullable = false)
    private int yearsFromNow;

    @Column(name = "p10", precision = 8, scale = 3)
    private @Nullable BigDecimal p10;

    @Column(name = "p50", precision = 8, scale = 3)
    private @Nullable BigDecimal p50;

    @Column(name = "p90", precision = 8, scale = 3)
    private @Nullable BigDecimal p90;

    protected PredictionPointEmbeddable() {}

    public PredictionPointEmbeddable(
            String scenario,
            String itemCode,
            int yearsFromNow,
            @Nullable BigDecimal p10,
            @Nullable BigDecimal p50,
            @Nullable BigDecimal p90) {
        this.scenario = scenario;
        this.itemCode = itemCode;
        this.yearsFromNow = yearsFromNow;
        this.p10 = p10;
        this.p50 = p50;
        this.p90 = p90;
    }

    public String getScenario() {
        return scenario;
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getYearsFromNow() {
        return yearsFromNow;
    }

    public @Nullable BigDecimal getP10() {
        return p10;
    }

    public @Nullable BigDecimal getP50() {
        return p50;
    }

    public @Nullable BigDecimal getP90() {
        return p90;
    }
}
