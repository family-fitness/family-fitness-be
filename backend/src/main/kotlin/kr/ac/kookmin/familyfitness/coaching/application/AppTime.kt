package kr.ac.kookmin.familyfitness.coaching.application

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 모든 「지금」은 주입된 [Clock] 으로, 날짜는 앱 시간대(KST)로 계산한다. */
@Component
class AppTime(
    private val clock: Clock,
    val zone: ZoneId,
) {
    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.ofInstant(now(), zone)

    fun dateOf(instant: Instant): LocalDate = LocalDate.ofInstant(instant, zone)

    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    /** 이번 주 월요일. */
    fun thisWeekStart(): LocalDate = weekStartOf(today())

    companion object {
        fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
