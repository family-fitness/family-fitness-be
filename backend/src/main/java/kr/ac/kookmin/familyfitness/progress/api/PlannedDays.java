package kr.ac.kookmin.familyfitness.progress.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * 그 사람에게 운동이 잡힌 날. 이어서 한 날(streakDays)은 잡힌 날을 빼먹으면 끊긴다(결정 25).
 * 미션은 coaching 이 갖고 있지만 progress 는 coaching 을 참조하지 않는다(coaching → progress::api 한 방향).
 * 그래서 인터페이스를 여기 두고 coaching 이 구현한다(identity.api.MissionLookup 과 같은 의존 역전).
 */
public interface PlannedDays {
    /**
     * {@code from}~{@code to}(양끝 포함) 안에서 이 프로필이 참여자인 운동이 서는 날.
     * 하루짜리 미션은 그날, 여러 날짜리는 FE 요청서 4장의 규칙(fe:src/mocks/history.ts standsOn)을 따른다.
     */
    Set<LocalDate> plannedDays(UUID profileId, LocalDate from, LocalDate to);
}
