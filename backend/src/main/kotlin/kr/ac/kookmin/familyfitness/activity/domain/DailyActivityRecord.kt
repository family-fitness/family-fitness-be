package kr.ac.kookmin.familyfitness.activity.domain

import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 한 프로필의 (날짜, 출처) 당 한 행. 걸음수는 그날 총량으로 덮어쓰고(MANUAL), 분은 누적한다(TIMER·VIDEO).
 * 규칙: 걸음 0~100000 · 분 > 0 · 분의 출처는 서버가 아는 TIMER·VIDEO 만.
 */
class DailyActivityRecord(
    val id: UUID,
    val profileId: UUID,
    val activityDate: LocalDate,
    val source: ActivitySource,
    steps: Int,
    activeMinutes: Int,
    recordedAt: Instant,
) {
    var steps: Int = steps
        private set
    var activeMinutes: Int = activeMinutes
        private set
    var recordedAt: Instant = recordedAt
        private set

    fun overwriteSteps(
        steps: Int,
        at: Instant,
    ) {
        validateSteps(steps)
        this.steps = steps
        this.recordedAt = at
    }

    fun addActiveMinutes(
        minutes: Int,
        at: Instant,
    ) {
        validateMinutes(minutes)
        this.activeMinutes += minutes
        this.recordedAt = at
    }

    fun toDailyActivity() = DailyActivity(profileId, activityDate, source, steps, activeMinutes)

    companion object {
        const val MAX_STEPS = 100_000

        fun newSteps(
            profileId: UUID,
            activityDate: LocalDate,
            steps: Int,
            at: Instant,
        ): DailyActivityRecord {
            validateSteps(steps)
            return DailyActivityRecord(UUID.randomUUID(), profileId, activityDate, ActivitySource.MANUAL, steps, 0, at)
        }

        fun newMinutes(
            profileId: UUID,
            activityDate: LocalDate,
            source: ActivitySource,
            minutes: Int,
            at: Instant,
        ): DailyActivityRecord {
            validateMinuteSource(source)
            validateMinutes(minutes)
            return DailyActivityRecord(UUID.randomUUID(), profileId, activityDate, source, 0, minutes, at)
        }

        fun validateSteps(steps: Int) {
            require(steps in 0..MAX_STEPS) { "걸음수는 0~$MAX_STEPS 사이여야 합니다: $steps" }
        }

        fun validateMinutes(minutes: Int) {
            require(minutes > 0) { "활동 분은 0보다 커야 합니다: $minutes" }
        }

        fun validateMinuteSource(source: ActivitySource) {
            require(source.serverVerified) { "활동 분의 출처는 TIMER·VIDEO 만 가능합니다: $source" }
        }
    }
}
