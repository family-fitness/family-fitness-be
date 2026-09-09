package kr.ac.kookmin.familyfitness.activity.api

import java.time.LocalDate
import java.util.UUID

/** 활동 출처. TIMER·VIDEO 만 서버가 진짜로 안다. 웹(PWA)이라 WATCH 는 존재하지 않는다. */
enum class ActivitySource(
    val serverVerified: Boolean,
) {
    MANUAL(false),
    TIMER(true),
    VIDEO(true),
}

data class DailyActivity(
    val profileId: UUID,
    val activityDate: LocalDate,
    val source: ActivitySource,
    val steps: Int,
    val activeMinutes: Int,
)

data class ActivityTotals(
    val steps: Int,
    /** 모든 출처 합계 — 「사람이 말한 활동」 포함 */
    val activeMinutes: Int,
    /** TIMER + VIDEO — 「서버가 아는 활동」 */
    val verifiedMinutes: Int,
)

/** 활동 기록. 미션 진행도 갱신은 coaching 이 이 결과를 받아서 한다. */
interface ActivityRecorder {
    /** 걸음수는 그날 총량으로 덮어쓴다(누적 아님). source = MANUAL. */
    fun overwriteSteps(
        profileId: UUID,
        activityDate: LocalDate,
        steps: Int,
    ): DailyActivity

    /** 분을 누적한다. source 는 TIMER 또는 VIDEO. 그날 해당 출처의 누적 결과를 돌려준다. */
    fun addActiveMinutes(
        profileId: UUID,
        activityDate: LocalDate,
        source: ActivitySource,
        minutes: Int,
    ): DailyActivity
}

interface ActivityQuery {
    /** [from]~[to] (양끝 포함) 합계. */
    fun totals(
        profileId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): ActivityTotals

    /** 그날 모든 출처의 활동 분 합계. */
    fun activeMinutesOn(
        profileId: UUID,
        activityDate: LocalDate,
    ): Int
}
