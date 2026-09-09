package kr.ac.kookmin.familyfitness.fitness.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** 예측 시나리오. IMPROVE 는 AI 가 내지 않는다(횡단면 자료) → MAINTAIN 만 저장 (▲ AI-13 §3.7). */
enum class PredictionScenario {
    MAINTAIN,
    IMPROVE,
}

data class PredictionPoint(
    val scenario: PredictionScenario,
    val itemCode: String,
    val yearsFromNow: Int,
    val p10: BigDecimal?,
    val p50: BigDecimal?,
    val p90: BigDecimal?,
)

/** AI `trajectory` 응답을 그대로 굳힌 예측. 기준 측정 회차가 삭제돼도 남도록 fitnessTestId 는 참조만 한다. */
class Prediction(
    val id: UUID,
    val profileId: UUID,
    val fitnessTestId: UUID?,
    val itemCode: String,
    val horizonYears: Int,
    val modelVersion: String,
    val basis: String,
    val notice: String,
    val points: List<PredictionPoint>,
    val createdAt: Instant,
) {
    companion object {
        /** AI 응답에 model version 이 없어 고정한다 (▲ 확정 필요). */
        const val MODEL_VERSION = "ai-trajectory-v1"
    }
}
