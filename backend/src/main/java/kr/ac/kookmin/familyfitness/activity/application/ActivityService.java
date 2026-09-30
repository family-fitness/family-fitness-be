package kr.ac.kookmin.familyfitness.activity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.activity.api.ActivityTotals;
import kr.ac.kookmin.familyfitness.activity.api.DailyActivity;
import kr.ac.kookmin.familyfitness.activity.api.DailyMinutes;
import kr.ac.kookmin.familyfitness.activity.api.VerifiedSummary;
import kr.ac.kookmin.familyfitness.activity.application.port.ActivityDailyRepository;
import kr.ac.kookmin.familyfitness.activity.domain.DailyActivityRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 기록의 공개 API 구현. (profile, date, source) 한 행을 upsert 한다. 시간은 초로 쌓는다(분 적립도 초로 바꿔 쌓는다).
 * 권한·미션 진행도는 호출 모듈(coaching)의 책임이고, 여기서는 값 규칙만 지킨다.
 */
@Service
public class ActivityService implements ActivityRecorder, ActivityQuery {
    private final ActivityDailyRepository repository;
    private final Clock clock;

    public ActivityService(ActivityDailyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DailyActivity overwriteSteps(UUID profileId, LocalDate activityDate, int steps) {
        Instant now = clock.instant();
        DailyActivityRecord existing = repository.find(profileId, activityDate, ActivitySource.MANUAL);
        DailyActivityRecord record;
        if (existing == null) {
            record = DailyActivityRecord.newSteps(profileId, activityDate, steps, now);
        } else {
            existing.overwriteSteps(steps, now);
            record = existing;
        }
        return repository.save(record).toDailyActivity();
    }

    @Override
    @Transactional
    public DailyActivity addActiveMinutes(UUID profileId, LocalDate activityDate, ActivitySource source, int minutes) {
        DailyActivityRecord.validateMinutes(minutes);
        return addActiveSeconds(profileId, activityDate, source, Math.multiplyExact(minutes, 60));
    }

    @Override
    @Transactional
    public DailyActivity addActiveSeconds(UUID profileId, LocalDate activityDate, ActivitySource source, int seconds) {
        DailyActivityRecord.validateMinuteSource(source);
        Instant now = clock.instant();
        DailyActivityRecord existing = repository.find(profileId, activityDate, source);
        DailyActivityRecord record;
        if (existing == null) {
            record = DailyActivityRecord.newSeconds(profileId, activityDate, source, seconds, now);
        } else {
            existing.addActiveSeconds(seconds, now);
            record = existing;
        }
        return repository.save(record).toDailyActivity();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityTotals totals(UUID profileId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("기간의 끝이 시작보다 앞섭니다: " + from + " ~ " + to);
        }
        return repository.totals(profileId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public int activeMinutesOn(UUID profileId, LocalDate activityDate) {
        return repository.activeMinutesOn(profileId, activityDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyMinutes> verifiedDays(UUID profileId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("기간의 끝이 시작보다 앞섭니다: " + from + " ~ " + to);
        }
        return repository.verifiedDays(profileId, from, to);
    }

    /** 기본 구현(프로필마다 한 번)을 쿼리 한 번으로 바꾼다. */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<DailyMinutes>> verifiedDaysOf(Collection<UUID> profileIds, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("기간의 끝이 시작보다 앞섭니다: " + from + " ~ " + to);
        }
        return repository.verifiedDaysOf(profileIds, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public VerifiedSummary verifiedSummary(UUID profileId) {
        return repository.verifiedSummary(profileId);
    }
}
