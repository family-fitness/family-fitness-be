package kr.ac.kookmin.familyfitness.coaching.application

import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주간 코치 자동 실행 — 일요일 20:00(KST), 가족 단위(보드 F3 「스케줄 일요일 20:00」).
 * 가족마다 [CoachRunService.startScheduled] 를 부르고, 편성은 평소처럼 비동기 파이프라인이 한다.
 * `app.coach.schedule.enabled=false` 로 끌 수 있다(테스트·로컬 기본은 켜져 있지만 일요일 20시에만 돈다).
 */
@Component
@ConditionalOnProperty(name = ["app.coach.schedule.enabled"], havingValue = "true", matchIfMissing = true)
class CoachRunScheduler(
    private val profileQuery: ProfileQuery,
    private val coachRuns: CoachRunService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.coach.schedule.cron:0 0 20 * * SUN}", zone = "\${app.timezone:Asia/Seoul}")
    fun runWeekly() {
        val families = profileQuery.allFamilyIds()
        var started = 0
        families.forEach { familyId ->
            runCatching { coachRuns.startScheduled(familyId) }
                .onSuccess { if (it != null) started++ }
                .onFailure { log.error("주간 코치 스케줄 실패: family={}", familyId, it) }
        }
        log.info("주간 코치 스케줄: 가족 {}곳 중 {}곳 실행", families.size, started)
    }
}
