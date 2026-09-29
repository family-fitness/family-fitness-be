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

/** `fitness_grade_thresholds` — 읽기 전용 국민체력100 등급 기준표 한 줄. 적재는 마이그레이션(`grade_thresholds_to_sql.py`)이 한다. */
@Entity
@Table(name = "fitness_grade_thresholds")
public class FitnessGradeThresholdEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private @Nullable Long id;

    @Column(name = "age_group", nullable = false, length = 10)
    private String ageGroup;

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

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "item_code", nullable = false, length = 3)
    private String itemCode;

    @Column(name = "op", nullable = false, length = 10)
    private String op;

    @Column(name = "cutoff", nullable = false, precision = 8, scale = 3)
    private BigDecimal cutoff;

    @Column(name = "cutoff_upper", precision = 8, scale = 3)
    private @Nullable BigDecimal cutoffUpper;

    protected FitnessGradeThresholdEntity() {}

    public String getAgeGroup() {
        return ageGroup;
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

    public String getGrade() {
        return grade;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getOp() {
        return op;
    }

    public BigDecimal getCutoff() {
        return cutoff;
    }

    public @Nullable BigDecimal getCutoffUpper() {
        return cutoffUpper;
    }
}
