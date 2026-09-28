package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 여러 사람의 잡힌 날을 한 번에 셀 때 쓰는 한 줄 — 누구의 미션인가({@code profileId}), 그 미션을 언제 만들었나.
 *
 * @param missionCreatedAt 미션 행이 만들어진 시각(missions.created_at). 승인한 제안이면 승인한 시각이다
 */
public record ParticipantSpan(UUID profileId, MissionSpan span, Instant missionCreatedAt) {
    /**
     * {@link MissionSpan#standingDays} 가운데 미션을 만든 날(KST)과 같거나 뒤인 날. 리그 달성률의 분모다(결정 41).
     * 만들기 전 날은 그 사람에게 운동이 「잡혀 있던」 날이 아니다 — 지난 날짜로 만든 미션이 안 한 날을 늘리지 못한다.
     */
    public List<LocalDate> standingDaysSinceCreated(LocalDate today, ZoneId zone) {
        LocalDate createdOn = LocalDate.ofInstant(missionCreatedAt, zone);
        return span.standingDays(today, zone).stream()
                .filter(day -> !day.isBefore(createdOn))
                .toList();
    }
}
