package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSpan;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantSpan;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDays;
import kr.ac.kookmin.familyfitness.progress.api.PlannedDaysSinceCreated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「잡힌 날」. progress 가 이어서 한 날을 셀 때 {@link PlannedDays} 로, league 가 달성률을 셀 때 {@link PlannedDaysSinceCreated} 로 부른다.
 * 그 사람이 참여자인 미션이 그날 서면 잡힌 날이다. 서는 날의 규칙은 {@link MissionSpan#standingDays} 에 있다.
 * 「오늘」 은 앱 시간대(KST)다.
 */
@Service
@Transactional(readOnly = true)
public class PlannedDaysService implements PlannedDays, PlannedDaysSinceCreated {
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
            span.standingDays(today, time.getZone()).stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .forEach(days::add);
        }
        return days;
    }

    /** 서는 날 가운데 미션을 만든 날(KST) 이후만 — {@link ParticipantSpan#standingDaysSinceCreated}. */
    @Override
    public Map<UUID, Set<LocalDate>> plannedDaysSinceCreated(
            Collection<UUID> profileIds, LocalDate from, LocalDate to) {
        Map<UUID, Set<LocalDate>> days = new HashMap<>();
        if (profileIds.isEmpty() || to.isBefore(from)) return days;
        LocalDate today = time.today();
        for (ParticipantSpan row : missions.participantSpansOf(profileIds, from, to)) {
            row.standingDaysSinceCreated(today, time.getZone()).stream()
                    .filter(it -> !it.isBefore(from) && !it.isAfter(to))
                    .forEach(it -> days.computeIfAbsent(row.profileId(), k -> new TreeSet<>())
                            .add(it));
        }
        return days;
    }
}
