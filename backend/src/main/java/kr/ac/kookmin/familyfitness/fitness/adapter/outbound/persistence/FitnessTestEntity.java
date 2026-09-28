package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** `fitness_tests` + `fitness_test_items`. 회차와 항목은 항상 함께 저장되고 바뀌지 않는다. */
@Entity
@Table(name = "fitness_tests")
public class FitnessTestEntity {
    /**
     * 여러 회차의 항목을 IN 한 번에 읽을 회차 수. 측정 이력 상한(100)과 같게 둬서 이력 한 번 조회에 항목 select 가 한 번이다.
     * 상한이 이보다 커져도 IN 조회가 몇 번으로 나뉠 뿐 회차마다 select 가 나가지는 않는다.
     */
    private static final int ITEMS_BATCH_SIZE = 100;

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "tested_on", nullable = false)
    private LocalDate testedOn;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_at_test", nullable = false)
    private int ageAtTest;

    @Column(name = "height_cm", precision = 4, scale = 1)
    private @Nullable BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 4, scale = 1)
    private @Nullable BigDecimal weightKg;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 이력 조회(여러 회차)에서 회차마다 항목 select 가 따로 나가지 않게 IN 으로 묶어 읽는다. */
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = ITEMS_BATCH_SIZE)
    @CollectionTable(name = "fitness_test_items", joinColumns = @JoinColumn(name = "fitness_test_id"))
    private List<FitnessTestItemEmbeddable> items = new ArrayList<>();

    protected FitnessTestEntity() {}

    public FitnessTestEntity(
            UUID id,
            UUID profileId,
            LocalDate testedOn,
            String source,
            int ageAtTest,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            Instant createdAt,
            List<FitnessTestItemEmbeddable> items) {
        this.id = id;
        this.profileId = profileId;
        this.testedOn = testedOn;
        this.source = source;
        this.ageAtTest = ageAtTest;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.createdAt = createdAt;
        this.items = new ArrayList<>(items);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public LocalDate getTestedOn() {
        return testedOn;
    }

    public String getSource() {
        return source;
    }

    public int getAgeAtTest() {
        return ageAtTest;
    }

    public @Nullable BigDecimal getHeightCm() {
        return heightCm;
    }

    public @Nullable BigDecimal getWeightKg() {
        return weightKg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<FitnessTestItemEmbeddable> getItems() {
        return items;
    }
}
