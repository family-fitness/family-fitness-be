package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import org.jspecify.annotations.Nullable;

/**
 * 하루 편성 요청: 누구(profileId)의 어느 날(date)을 어떤 조건으로.
 * latitude · longitude 는 날씨를 볼 위치다. 없으면 날씨 어댑터의 기본 위치(서울시청)로 본다.
 */
public record StartCoachRunCommand(
        UUID profileId,
        LocalDate date,
        CoachRunConditions conditions,
        @Nullable Double latitude,
        @Nullable Double longitude) {

    public StartCoachRunCommand(UUID profileId, LocalDate date, CoachRunConditions conditions) {
        this(profileId, date, conditions, null, null);
    }
}
