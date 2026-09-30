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

/** `fitness_grade_distribution` — 읽기 전용 또래 등급 비율 한 줄. 적재는 마이그레이션(`grade_distribution_to_sql.py`)이 한다. */
@Entity
@Table(name = "fitness_grade_distribution")
public class FitnessGradeDistributionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private @Nullable Long id;

    @Column(name = "age_group", nullable = false, length = 10)
    private String ageGroup;

    @Column(name = "sex", nullable = false, length = 1)
    private String sex;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age", nullable = false)
    private int age;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "n", nullable = false)
    private int n;

    @Column(name = "ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal ratio;

    protected FitnessGradeDistributionEntity() {}

    public String getAgeGroup() {
        return ageGroup;
    }

    public String getSex() {
        return sex;
    }

    public int getAge() {
        return age;
    }

    public String getGrade() {
        return grade;
    }

    public int getN() {
        return n;
    }

    public BigDecimal getRatio() {
        return ratio;
    }
}
