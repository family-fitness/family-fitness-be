package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** `fitness_norms` — 읽기 전용 규준 행. 적재는 마이그레이션/시드가 한다. */
@Entity
@Table(name = "fitness_norms")
class FitnessNormEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "item_code", nullable = false, length = 3)
    var itemCode: String,
    @Column(name = "sex", nullable = false, length = 1)
    var sex: String,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_from", nullable = false)
    var ageFrom: Int,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_to", nullable = false)
    var ageTo: Int,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "percentile", nullable = false)
    var percentile: Int,
    @Column(name = "norm_value", nullable = false, precision = 8, scale = 3)
    var normValue: BigDecimal,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "source_year", nullable = false)
    var sourceYear: Int,
)

@Embeddable
class FitnessTestItemEmbeddable(
    @Column(name = "item_code", nullable = false, length = 3)
    var itemCode: String,
    @Column(name = "raw_value", nullable = false, precision = 8, scale = 3)
    var rawValue: BigDecimal,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "percentile")
    var percentile: Int?,
    @Column(name = "grade", length = 10)
    var grade: String?,
    @Column(name = "band", length = 10)
    var band: String?,
)

/** `fitness_tests` + `fitness_test_items`. 회차와 항목은 항상 함께 저장되고 바뀌지 않는다. */
@Entity
@Table(name = "fitness_tests")
class FitnessTestEntity(
    @Id
    @Column(name = "id")
    var id: UUID,
    @Column(name = "profile_id", nullable = false)
    var profileId: UUID,
    @Column(name = "tested_on", nullable = false)
    var testedOn: LocalDate,
    @Column(name = "source", nullable = false, length = 20)
    var source: String,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "age_at_test", nullable = false)
    var ageAtTest: Int,
    @Column(name = "height_cm", precision = 4, scale = 1)
    var heightCm: BigDecimal?,
    @Column(name = "weight_kg", precision = 4, scale = 1)
    var weightKg: BigDecimal?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fitness_test_items", joinColumns = [JoinColumn(name = "fitness_test_id")])
    var items: MutableList<FitnessTestItemEmbeddable> = mutableListOf(),
)

@Embeddable
class PredictionPointEmbeddable(
    @Column(name = "scenario", nullable = false, length = 20)
    var scenario: String,
    @Column(name = "item_code", nullable = false, length = 3)
    var itemCode: String,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "years_from_now", nullable = false)
    var yearsFromNow: Int,
    @Column(name = "p10", precision = 8, scale = 3)
    var p10: BigDecimal?,
    @Column(name = "p50", precision = 8, scale = 3)
    var p50: BigDecimal?,
    @Column(name = "p90", precision = 8, scale = 3)
    var p90: BigDecimal?,
)

/** `predictions` + `prediction_points`. AI 응답을 그대로 굳힌다. */
@Entity
@Table(name = "predictions")
class PredictionEntity(
    @Id
    @Column(name = "id")
    var id: UUID,
    @Column(name = "profile_id", nullable = false)
    var profileId: UUID,
    @Column(name = "fitness_test_id")
    var fitnessTestId: UUID?,
    @Column(name = "item_code", nullable = false, length = 3)
    var itemCode: String,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "horizon_years", nullable = false)
    var horizonYears: Int,
    @Column(name = "model_version", nullable = false, length = 40)
    var modelVersion: String,
    @Column(name = "basis", nullable = false, length = 60)
    var basis: String,
    @Column(name = "notice", nullable = false, length = 300)
    var notice: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "prediction_points", joinColumns = [JoinColumn(name = "prediction_id")])
    var points: MutableList<PredictionPointEmbeddable> = mutableListOf(),
)
