package kr.ac.kookmin.familyfitness.fitness.api

import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class FactorPoint(
    val factor: FitnessFactor,
    val itemCode: String,
    val percentile: Int,
)

/** 코치 편성(assess 단계)이 읽는 최신 측정 요약. */
data class LatestFitness(
    val profileId: UUID,
    val fitnessTestId: UUID,
    val testedOn: LocalDate,
    val heightCm: BigDecimal?,
    val weightKg: BigDecimal?,
    /** itemCode → 원시 측정값. 005·006 은 애초에 저장되지 않는다. */
    val measurements: Map<String, BigDecimal>,
    val weakest: FactorPoint?,
    val strongest: FactorPoint?,
)

interface FitnessQuery {
    fun latestOf(profileId: UUID): LatestFitness?

    /** 가족 중 측정 기록이 한 명이라도 있는가 (`NO_MEASURED_MEMBER` 판정). */
    fun hasAnyTest(profileIds: Collection<UUID>): Boolean
}
