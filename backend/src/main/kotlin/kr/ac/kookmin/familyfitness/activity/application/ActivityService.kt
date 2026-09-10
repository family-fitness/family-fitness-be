package kr.ac.kookmin.familyfitness.activity.application

import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * 활동 기록의 공개 API 구현. (profile, date, source) 한 행을 upsert 한다.
 * 권한·미션 진행도는 호출 모듈(coaching)의 책임이고, 여기서는 값 규칙만 지킨다.
 */
@Service
class ActivityService(
    private val repository: ActivityDailyRepository,
    private val clock: Clock,
) : ActivityRecorder,
    ActivityQuery {
    @Transactional
    override fun overwriteSteps(
        profileId: UUID,
        activityDate: LocalDate,
        steps: Int,
    ): DailyActivity {
        val now = clock.instant()
        val record =
            repository.find(profileId, activityDate, ActivitySource.MANUAL)?.apply { overwriteSteps(steps, now) }
                ?: DailyActivityRecord.newSteps(profileId, activityDate, steps, now)
        return repository.save(record).toDailyActivity()
    }

    @Transactional
    override fun addActiveMinutes(
        profileId: UUID,
        activityDate: LocalDate,
        source: ActivitySource,
        minutes: Int,
    ): DailyActivity {
        DailyActivityRecord.validateMinuteSource(source)
        val now = clock.instant()
        val record =
            repository.find(profileId, activityDate, source)?.apply { addActiveMinutes(minutes, now) }
                ?: DailyActivityRecord.newMinutes(profileId, activityDate, source, minutes, now)
        return repository.save(record).toDailyActivity()
    }

    @Transactional(readOnly = true)
    override fun totals(
        profileId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): ActivityTotals {
        require(!to.isBefore(from)) { "기간의 끝이 시작보다 앞섭니다: $from ~ $to" }
        return repository.totals(profileId, from, to)
    }

    @Transactional(readOnly = true)
    override fun activeMinutesOn(
        profileId: UUID,
        activityDate: LocalDate,
    ): Int = repository.activeMinutesOn(profileId, activityDate)
}
