package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `fitness_value_quantiles` — 읽기 전용 또래 분포 한 줄. 적재는 마이그레이션(`value_quantiles_to_sql.py`)이 한다. */
@Entity
@Table(name = "fitness_value_quantiles")
public class FitnessValueQuantileEntity {
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
    @Column(name = "age", nullable = false)
    private int age;

    @Column(name = "item_code", nullable = false, length = 3)
    private String itemCode;

    @Column(name = "n", nullable = false)
    private int n;

    @Column(name = "quantiles", nullable = false, length = 1000)
    private String quantiles;

    protected FitnessValueQuantileEntity() {}

    public String getAgeGroup() {
        return ageGroup;
    }

    public String getSex() {
        return sex;
    }

    public String getAgeUnit() {
        return ageUnit;
    }

    public int getAge() {
        return age;
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getN() {
        return n;
    }

    public String getQuantiles() {
        return quantiles;
    }
}
