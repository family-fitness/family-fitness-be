package kr.ac.kookmin.familyfitness.identity.application

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** identity 의 모든 "지금"·"오늘". 나이 계산은 서비스 시간대(Asia/Seoul) 기준 날짜로 한다. */
@Component
class IdentityClock(
    private val clock: Clock,
    private val zone: ZoneId,
) {
    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.now(clock.withZone(zone))
}
