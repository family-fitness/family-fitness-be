package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * progress 가 이어서 한 날을 셀 때 부르는 「잡힌 날」({@link PlannedDays} 구현). 그 사람이 참여자인 미션이 그날 서면 잡힌 날이다.
 * 서는 날의 규칙은 {@link MissionSpan#standingDays} 에 있다. 여러 날짜리 미션은 칸을 끝낸 날(mission_session_completions
 * .completed_on)에 선다. 「오늘」 은 앱 시간대(KST)다.
 */
@Service
@Transactional(readOnly = true)
public class PlannedDaysService implements PlannedDays {
    private final MissionRepository missions;
    private final AppTime time;

    public PlannedDaysService(MissionRepository missions, AppTime time) {
        this.missions = missions;
        this.time = time;
    }

    @Override
    public Set<LocalDate> plannedDays(UUID profileId, LocalDate from, LocalDate to) {
        Set<LocalDate> days = new TreeSet<>();
        if (to.isBefore(from)) return days;
        LocalDate today = time.today();
        for (MissionSpan span : missions.spansOf(profileId, from, to)) {
            span.standingDays(today).stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .forEach(days::add);
        }
        return days;
    }
}
