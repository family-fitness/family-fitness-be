@file:Suppress("ktlint:standard:package-name")

package kr.ac.kookmin.familyfitness.fitness.adapter.`in`.web

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessItem
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource
import kr.ac.kookmin.familyfitness.fitness.domain.Grade
import kr.ac.kookmin.familyfitness.fitness.domain.InputGroup
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionScenario
import kr.ac.kookmin.familyfitness.fitness.domain.ValueRange
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Band
import kr.ac.kookmin.familyfitness.shared.domain.Copy
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// ---- GET /fitness/items ----

data class FitnessItemsResponse(
    val ageGroup: AgeGroup,
    val items: List<Item>,
) {
    data class Item(
        val itemCode: String,
        val itemName: String,
        val itemLabel: String,
        val unit: String,
        val factor: FitnessFactor,
        val higherIsBetter: Boolean,
        val inputGroup: InputGroup,
        val optional: Boolean,
        val equipment: String?,
        val range: ValueRange,
    )

    companion object {
        fun of(ageGroup: AgeGroup): FitnessItemsResponse =
            FitnessItemsResponse(
                ageGroup = ageGroup,
                items =
                    FitnessItem.forAgeGroup(ageGroup).map {
                        Item(
                            itemCode = it.code,
                            itemName = it.itemName,
                            itemLabel = it.label(ageGroup),
                            unit = it.unit,
                            factor = it.factor,
                            higherIsBetter = it.higherIsBetter,
                            inputGroup = it.inputGroup,
                            optional = it.optional,
                            equipment = it.equipment,
                            range = it.range,
                        )
                    },
            )
    }
}

// ---- POST /profiles/{profileId}/fitness-tests ----

data class RegisterFitnessTestRequest(
    @field:NotNull val testedOn: LocalDate?,
    @field:NotNull val source: FitnessTestSource?,
    @field:DecimalMin("30") @field:DecimalMax("230") val heightCm: BigDecimal?,
    @field:DecimalMin("5") @field:DecimalMax("250") val weightKg: BigDecimal?,
    /** 비어 있으면 도메인이 400 NO_ITEMS 로 거부한다 — 여기서 @NotEmpty 로 막지 않는다. */
    @field:NotNull @field:Valid val items: List<ItemInput>?,
) {
    data class ItemInput(
        @field:NotBlank val itemCode: String?,
        @field:NotNull @field:Digits(integer = 5, fraction = 3) val value: BigDecimal?,
    )

    fun measurements(): List<Measurement> = items!!.map { Measurement(it.itemCode!!, it.value!!) }
}

data class ItemResult(
    val itemCode: String,
    val itemLabel: String,
    val unit: String,
    val value: BigDecimal,
    val percentile: Int?,
    val grade: Grade?,
    val band: Band?,
    val topPercentText: String?,
)

data class FitnessTestResponse(
    val fitnessTestId: UUID,
    val testedOn: LocalDate,
    val items: List<ItemResult>,
    val weakest: FactorPoint?,
    val strongest: FactorPoint?,
    val disclaimer: String = Copy.FITNESS_DISCLAIMER,
) {
    companion object {
        fun of(test: FitnessTest) =
            FitnessTestResponse(
                fitnessTestId = test.id,
                testedOn = test.testedOn,
                items = test.itemResults(),
                weakest = test.weakest,
                strongest = test.strongest,
            )
    }
}

// ---- GET /profiles/{profileId}/fitness-tests/latest ----

data class RadarPointResponse(
    val factor: FitnessFactor,
    val percentile: Int?,
)

/** 이력이 없어도 200 — id·날짜 null, 레이더 5요인 percentile null, 항목 빈 목록. */
data class LatestFitnessResponse(
    val fitnessTestId: UUID?,
    val testedOn: LocalDate?,
    val radar: List<RadarPointResponse>,
    val items: List<ItemResult>,
    val weakest: FactorPoint?,
    val strongest: FactorPoint?,
    val coachDirection: CoachDirection,
    val disclaimer: String = Copy.FITNESS_DISCLAIMER,
) {
    companion object {
        fun of(test: FitnessTest?): LatestFitnessResponse =
            if (test == null) {
                LatestFitnessResponse(
                    fitnessTestId = null,
                    testedOn = null,
                    radar = FitnessFactor.RADAR.map { RadarPointResponse(it, null) },
                    items = emptyList(),
                    weakest = null,
                    strongest = null,
                    coachDirection = CoachDirection.GROWTH,
                )
            } else {
                LatestFitnessResponse(
                    fitnessTestId = test.id,
                    testedOn = test.testedOn,
                    radar = test.radar().map { RadarPointResponse(it.factor, it.percentile) },
                    items = test.itemResults(),
                    weakest = test.weakest,
                    strongest = test.strongest,
                    coachDirection = test.coachDirection,
                )
            }
    }
}

private fun FitnessTest.itemResults(): List<ItemResult> =
    items.map {
        ItemResult(
            itemCode = it.item.code,
            itemLabel = it.item.label(ageGroup),
            unit = it.item.unit,
            value = it.value,
            percentile = it.score.percentile,
            grade = it.score.grade,
            band = it.score.band,
            topPercentText = it.score.topPercentText,
        )
    }

// ---- POST /profiles/{profileId}/predictions ----

data class PredictRequest(
    val fitnessTestId: UUID? = null,
    @field:Min(1) @field:Max(10) val horizonYears: Int? = null,
    val itemCode: String? = null,
)

data class PredictionResponse(
    val predictionId: UUID,
    val modelVersion: String,
    val basis: String,
    val points: List<Point>,
    val notice: String,
) {
    data class Point(
        val scenario: PredictionScenario,
        val itemCode: String,
        val yearsFromNow: Int,
        val p10: BigDecimal?,
        val p50: BigDecimal?,
        val p90: BigDecimal?,
    )

    companion object {
        fun of(prediction: Prediction) =
            PredictionResponse(
                predictionId = prediction.id,
                modelVersion = prediction.modelVersion,
                basis = prediction.basis,
                points = prediction.points.map { Point(it.scenario, it.itemCode, it.yearsFromNow, it.p10, it.p50, it.p90) },
                notice = prediction.notice,
            )
    }
}
