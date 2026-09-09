package kr.ac.kookmin.familyfitness.activity.application.port

import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord
import java.time.LocalDate
import java.util.UUID

interface ActivityDailyRepository {
    fun find(
        profileId: UUID,
        activityDate: LocalDate,
        source: ActivitySource,
    ): DailyActivityRecord?

    fun save(record: DailyActivityRecord): DailyActivityRecord

    /** [from]~[to] 양끝 포함 합계. */
    fun totals(
        profileId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): ActivityTotals

    fun activeMinutesOn(
        profileId: UUID,
        activityDate: LocalDate,
    ): Int
}
