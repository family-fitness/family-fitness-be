package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `fitness_norms` — 읽기 전용 규준 행. 적재는 마이그레이션/시드가 한다. */
@Entity
@Table(name = "fitness_norms")
public class FitnessNormEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private @Nullable Long id;

    @Column(name = "item_code", nullable = false, length = 3)
    private String itemCode;

    @Column(name = "sex", nullable = false, length = 1)
    private String sex;

    @Column(name = "age_unit", nullable = false, length = 4)
    private String ageUnit;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_from", nullable = false)
    private int ageFrom;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_to", nullable = false)
    private int ageTo;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "percentile", nullable = false)
    private int percentile;

    @Column(name = "norm_value", nullable = false, precision = 8, scale = 3)
    private BigDecimal normValue;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "source_year", nullable = false)
    private int sourceYear;

    protected FitnessNormEntity() {}

    public FitnessNormEntity(
            @Nullable Long id,
            String itemCode,
            String sex,
            String ageUnit,
            int ageFrom,
            int ageTo,
            int percentile,
            BigDecimal normValue,
            int sourceYear) {
        this.id = id;
        this.itemCode = itemCode;
        this.sex = sex;
        this.ageUnit = ageUnit;
        this.ageFrom = ageFrom;
        this.ageTo = ageTo;
        this.percentile = percentile;
        this.normValue = normValue;
        this.sourceYear = sourceYear;
    }

    public @Nullable Long getId() {
        return id;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getSex() {
        return sex;
    }

    public String getAgeUnit() {
        return ageUnit;
    }

    public int getAgeFrom() {
        return ageFrom;
    }

    public int getAgeTo() {
        return ageTo;
    }

    public int getPercentile() {
        return percentile;
    }

    public BigDecimal getNormValue() {
        return normValue;
    }

    public int getSourceYear() {
        return sourceYear;
    }
}
